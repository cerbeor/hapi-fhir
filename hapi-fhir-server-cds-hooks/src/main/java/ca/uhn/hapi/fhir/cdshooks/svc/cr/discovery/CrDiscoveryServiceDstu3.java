/*-
 * #%L
 * HAPI FHIR - CDS Hooks
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.hapi.fhir.cdshooks.svc.cr.discovery;

import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.hapi.fhir.cdshooks.api.json.CdsServiceJson;
import ca.uhn.hapi.fhir.cdshooks.svc.cr.CdsCrUtils;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DataRequirement;
import org.hl7.fhir.dstu3.model.Library;
import org.hl7.fhir.dstu3.model.PlanDefinition;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.opencds.cqf.fhir.api.Repository;
import org.opencds.cqf.fhir.utility.dstu3.SearchHelper;

import java.util.ArrayList;
import java.util.List;

@Deprecated(since = "8.1.4", forRemoval = true)
public class CrDiscoveryServiceDstu3 implements ICrDiscoveryService {

	protected final String PATIENT_ID_CONTEXT = "{{context.patientId}}";
	protected final int DEFAULT_MAX_URI_LENGTH = 8000;
	protected int myMaxUriLength;

	protected Repository myRepository;
	protected final IIdType myPlanDefinitionId;

	public CrDiscoveryServiceDstu3(IIdType thePlanDefinitionId, Repository theRepository) {
		myPlanDefinitionId = thePlanDefinitionId;
		myRepository = theRepository;
		myMaxUriLength = DEFAULT_MAX_URI_LENGTH;
	}

	public CdsServiceJson resolveService() {
		return resolveService(
				CdsCrUtils.readPlanDefinitionFromRepository(FhirVersionEnum.DSTU3, myRepository, myPlanDefinitionId));
	}

	protected CdsServiceJson resolveService(IBaseResource thePlanDefinition) {
		if (thePlanDefinition instanceof PlanDefinition) {
			PlanDefinition planDef = (PlanDefinition) thePlanDefinition;
			return new CrDiscoveryElementDstu3(planDef, getPrefetchUrlList(planDef)).getCdsServiceJson();
		}
		return null;
	}

	public boolean isEca(PlanDefinition thePlanDefinition) {
		if (thePlanDefinition.hasType() && thePlanDefinition.getType().hasCoding()) {
			for (Coding coding : thePlanDefinition.getType().getCoding()) {
				if (coding.getCode().equals("eca-rule")) {
					return true;
				}
			}
		}
		return false;
	}

	public Library resolvePrimaryLibrary(PlanDefinition thePlanDefinition) {
		// Assuming 1 library
		// TODO: enhance to handle multiple libraries - need a way to identify primary
		// library
		Library library = null;
		if (thePlanDefinition.hasLibrary()
				&& thePlanDefinition.getLibraryFirstRep().hasReference()) {
			library = myRepository.read(
					Library.class, thePlanDefinition.getLibraryFirstRep().getReferenceElement());
		}
		return library;
	}

	public List<String> resolveValueCodingCodes(List<Coding> theValueCodings) {
		List<String> result = new ArrayList<>();

		StringBuilder codes = new StringBuilder();
		for (Coding coding : theValueCodings) {
			if (coding.hasCode()) {
				String system = coding.getSystem();
				String code = coding.getCode();

				codes = getCodesStringBuilder(result, codes, system, code);
			}
		}

		result.add(codes.toString());
		return result;
	}

	public List<String> resolveValueSetCodes(StringType theValueSetId) {
		ValueSet valueSet = (ValueSet) SearchHelper.searchRepositoryByCanonical(myRepository, theValueSetId);
		List<String> result = new ArrayList<>();
		StringBuilder codes = new StringBuilder();
		if (valueSet.hasExpansion() && valueSet.getExpansion().hasContains()) {
			for (ValueSet.ValueSetExpansionContainsComponent contains :
					valueSet.getExpansion().getContains()) {
				String system = contains.getSystem();
				String code = contains.getCode();

				codes = getCodesStringBuilder(result, codes, system, code);
			}
		} else if (valueSet.hasCompose() && valueSet.getCompose().hasInclude()) {
			for (ValueSet.ConceptSetComponent concepts : valueSet.getCompose().getInclude()) {
				String system = concepts.getSystem();
				if (concepts.hasConcept()) {
					for (ValueSet.ConceptReferenceComponent concept : concepts.getConcept()) {
						String code = concept.getCode();

						codes = getCodesStringBuilder(result, codes, system, code);
					}
				}
			}
		}
		result.add(codes.toString());
		return result;
	}

	protected StringBuilder getCodesStringBuilder(
			List<String> theList, StringBuilder theCodes, String theSystem, String theCode) {
		String codeToken = theSystem + "|" + theCode;
		int postAppendLength = theCodes.length() + codeToken.length();

		if (theCodes.length() > 0 && postAppendLength < myMaxUriLength) {
			theCodes.append(",");
		} else if (postAppendLength > myMaxUriLength) {
			theList.add(theCodes.toString());
			theCodes = new StringBuilder();
		}
		theCodes.append(codeToken);
		return theCodes;
	}

	public List<String> createRequestUrl(DataRequirement theDataRequirement) {
		if (!isPatientCompartment(theDataRequirement.getType())) return null;
		String patientRelatedResource = theDataRequirement.getType() + "?"
				+ getPatientSearchParam(theDataRequirement.getType())
				+ "=Patient/" + PATIENT_ID_CONTEXT;
		List<String> ret = new ArrayList<>();
		if (theDataRequirement.hasCodeFilter()) {
			for (DataRequirement.DataRequirementCodeFilterComponent codeFilterComponent :
					theDataRequirement.getCodeFilter()) {
				if (!codeFilterComponent.hasPath()) continue;
				String path = mapCodePathToSearchParam(theDataRequirement.getType(), codeFilterComponent.getPath());

				StringType codeFilterComponentString = null;
				if (codeFilterComponent.hasValueSetStringType()) {
					codeFilterComponentString = codeFilterComponent.getValueSetStringType();
				} else if (codeFilterComponent.hasValueSetReference()) {
					codeFilterComponentString = new StringType(
							codeFilterComponent.getValueSetReference().getReference());
				} else if (codeFilterComponent.hasValueCoding()) {
					List<Coding> codeFilterValueCodings = codeFilterComponent.getValueCoding();
					boolean isFirstCodingInFilter = true;
					for (String code : resolveValueCodingCodes(codeFilterValueCodings)) {
						if (isFirstCodingInFilter) {
							ret.add(patientRelatedResource + "&" + path + "=" + code);
						} else {
							ret.add("," + code);
						}

						isFirstCodingInFilter = false;
					}
				}

				if (codeFilterComponentString != null) {
					for (String codes : resolveValueSetCodes(codeFilterComponentString)) {
						ret.add(patientRelatedResource + "&" + path + "=" + codes);
					}
				}
			}
			return ret;
		} else {
			ret.add(patientRelatedResource);
			return ret;
		}
	}

	public PrefetchUrlList getPrefetchUrlList(PlanDefinition thePlanDefinition) {
		PrefetchUrlList prefetchList = new PrefetchUrlList();
		if (thePlanDefinition == null) return null;
		if (!isEca(thePlanDefinition)) return null;
		Library library = resolvePrimaryLibrary(thePlanDefinition);
		// TODO: resolve data requirements
		if (!library.hasDataRequirement()) return null;
		for (DataRequirement dataRequirement : library.getDataRequirement()) {
			List<String> requestUrls = createRequestUrl(dataRequirement);
			if (requestUrls != null) {
				prefetchList.addAll(requestUrls);
			}
		}

		return prefetchList;
	}

	protected String mapCodePathToSearchParam(String theDataType, String thePath) {
		switch (theDataType) {
			case "MedicationAdministration":
				if (thePath.equals("medication")) return "code";
				break;
			case "MedicationDispense":
				if (thePath.equals("medication")) return "code";
				break;
			case "MedicationRequest":
				if (thePath.equals("medication")) return "code";
				break;
			case "MedicationStatement":
				if (thePath.equals("medication")) return "code";
				break;
			case "ProcedureRequest":
				if (thePath.equals("bodySite")) return "body-site";
				break;
			default:
				if (thePath.equals("vaccineCode")) return "vaccine-code";
				break;
		}
		return thePath.replace('.', '-').toLowerCase();
	}

	public static boolean isPatientCompartment(String theDataType) {
		if (theDataType == null) {
			return false;
		}
		switch (theDataType) {
			case "Account":
			case "AdverseEvent":
			case "AllergyIntolerance":
			case "Appointment":
			case "AppointmentResponse":
			case "AuditEvent":
			case "Basic":
			case "BodySite":
			case "CarePlan":
			case "CareTeam":
			case "ChargeItem":
			case "Claim":
			case "ClaimResponse":
			case "ClinicalImpression":
			case "Communication":
			case "CommunicationRequest":
			case "Composition":
			case "Condition":
			case "Consent":
			case "Coverage":
			case "DetectedIssue":
			case "DeviceRequest":
			case "DeviceUseStatement":
			case "DiagnosticReport":
			case "DocumentManifest":
			case "EligibilityRequest":
			case "Encounter":
			case "EnrollmentRequest":
			case "EpisodeOfCare":
			case "ExplanationOfBenefit":
			case "FamilyMemberHistory":
			case "Flag":
			case "Goal":
			case "Group":
			case "ImagingManifest":
			case "ImagingStudy":
			case "Immunization":
			case "ImmunizationRecommendation":
			case "List":
			case "MeasureReport":
			case "Media":
			case "MedicationAdministration":
			case "MedicationDispense":
			case "MedicationRequest":
			case "MedicationStatement":
			case "NutritionOrder":
			case "Observation":
			case "Patient":
			case "Person":
			case "Procedure":
			case "ProcedureRequest":
			case "Provenance":
			case "QuestionnaireResponse":
			case "ReferralRequest":
			case "RelatedPerson":
			case "RequestGroup":
			case "ResearchSubject":
			case "RiskAssessment":
			case "Schedule":
			case "Specimen":
			case "SupplyDelivery":
			case "SupplyRequest":
			case "VisionPrescription":
				return true;
			default:
				return false;
		}
	}

	public String getPatientSearchParam(String theDataType) {
		switch (theDataType) {
			case "Account":
				return "subject";
			case "AdverseEvent":
				return "subject";
			case "AllergyIntolerance":
				return "patient";
			case "Appointment":
				return "actor";
			case "AppointmentResponse":
				return "actor";
			case "AuditEvent":
				return "patient";
			case "Basic":
				return "patient";
			case "BodySite":
				return "patient";
			case "CarePlan":
				return "patient";
			case "CareTeam":
				return "patient";
			case "ChargeItem":
				return "subject";
			case "Claim":
				return "patient";
			case "ClaimResponse":
				return "patient";
			case "ClinicalImpression":
				return "subject";
			case "Communication":
				return "subject";
			case "CommunicationRequest":
				return "subject";
			case "Composition":
				return "subject";
			case "Condition":
				return "patient";
			case "Consent":
				return "patient";
			case "Coverage":
				return "patient";
			case "DetectedIssue":
				return "patient";
			case "DeviceRequest":
				return "subject";
			case "DeviceUseStatement":
				return "subject";
			case "DiagnosticReport":
				return "subject";
			case "DocumentManifest":
				return "subject";
			case "DocumentReference":
				return "subject";
			case "EligibilityRequest":
				return "patient";
			case "Encounter":
				return "patient";
			case "EnrollmentRequest":
				return "subject";
			case "EpisodeOfCare":
				return "patient";
			case "ExplanationOfBenefit":
				return "patient";
			case "FamilyMemberHistory":
				return "patient";
			case "Flag":
				return "patient";
			case "Goal":
				return "patient";
			case "Group":
				return "member";
			case "ImagingManifest":
				return "patient";
			case "ImagingStudy":
				return "patient";
			case "Immunization":
				return "patient";
			case "ImmunizationRecommendation":
				return "patient";
			case "List":
				return "subject";
			case "MeasureReport":
				return "patient";
			case "Media":
				return "subject";
			case "MedicationAdministration":
				return "patient";
			case "MedicationDispense":
				return "patient";
			case "MedicationRequest":
				return "subject";
			case "MedicationStatement":
				return "subject";
			case "NutritionOrder":
				return "patient";
			case "Observation":
				return "subject";
			case "Patient":
				return "_id";
			case "Person":
				return "patient";
			case "Procedure":
				return "patient";
			case "ProcedureRequest":
				return "patient";
			case "Provenance":
				return "patient";
			case "QuestionnaireResponse":
				return "subject";
			case "ReferralRequest":
				return "patient";
			case "RelatedPerson":
				return "patient";
			case "RequestGroup":
				return "subject";
			case "ResearchSubject":
				return "individual";
			case "RiskAssessment":
				return "subject";
			case "Schedule":
				return "actor";
			case "Specimen":
				return "subject";
			case "SupplyDelivery":
				return "patient";
			case "SupplyRequest":
				return "subject";
			case "VisionPrescription":
				return "patient";
		}

		return null;
	}
}
