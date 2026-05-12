package com.policyinsight.service;

import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SampleAgreementReportBuilder {

    public RiskReport build(String sampleKey, List<DocumentChunk> sources) {
        return switch (sampleKey) {
            case "vendor-agreement" -> buildVendorAgreement(sources);
            case "privacy-policy" -> buildPrivacyPolicy(sources);
            case "employment-policy" -> buildEmploymentPolicy(sources);
            case "campus-student-policy" -> buildCampusStudentPolicy(sources);
            default -> throw new SampleReportException("The requested sample was not found.");
        };
    }

    private RiskReport buildVendorAgreement(List<DocumentChunk> sources) {
        return new RiskReport(
                "Cedar Ridge Data Solutions, LLC will provide data operations support, dashboard configuration, and monthly performance reporting for Blue Harbor Merchandising, Inc. The agreement has a twelve-month initial term and total contract value of USD $96,000.",
                List.of(
                        claim("Client pays USD $8,000 per month during the initial term.", "USD $8,000", sources),
                        claim("Payment is due within fifteen (15) days after the invoice date.", "Payment is due within fifteen (15) days after the invoice date", sources),
                        claim("Deliverables are deemed accepted if the client does not reject them within ten (10) business days.", "ten (10) business days", sources),
                        claim("Provider must protect confidential information and process Client Data only as needed to provide the services.", "use Confidential Information only to perform or receive Services", sources)
                ),
                List.of(
                        claim("Provider must perform services described in Schedule A and applicable SOWs.", "Provider will perform the professional services described in Schedule A", sources),
                        claim("Client must provide access to requirements, sample data, systems, personnel, and approvals.", "Client will provide timely access", sources),
                        claim("Client must pay monthly fees and approved expenses.", "Client will pay Provider eight thousand dollars", sources),
                        claim("Receiving parties must protect confidential information using reasonable care.", "reasonable administrative, technical, and physical safeguards", sources)
                ),
                List.of(
                        claim("Provider is not required to perform out-of-scope work without a signed change order.", "Provider has no obligation to perform out-of-scope work until the change order is approved", sources),
                        claim("Provider may process Client Data only as necessary to provide services.", "Provider will process Client Data only as necessary to provide the Services", sources),
                        claim("Assignment requires prior written consent except for listed exceptions.", "Neither Party may assign this Agreement without the other Party's prior written consent", sources),
                        claim("Schedule A excludes ERP development, payment processing, regulated financial reporting, legal compliance advice, and production hosting unless added by change order.", "The Services do not include custom enterprise resource planning development", sources)
                ),
                List.of(
                        claim("Either party may terminate for material breach if not cured within thirty (30) days.", "other Party materially breaches", sources),
                        claim("Nonpayment may be cured within ten (10) days.", "Nonpayment may be cured within ten (10) days", sources),
                        claim("Client must pay undisputed fees and approved expenses through the termination date.", "effective termination date", sources),
                        claim("Provider may provide transition assistance for up to thirty (30) days at then-current rates.", "transition assistance for up to thirty (30) days", sources)
                ),
                List.of(
                        claim("Deliverables can be deemed accepted if the client misses the review window.", "ten (10) business days", sources),
                        claim("Overdue payments may accrue monthly interest.", "one percent (1%) per month", sources),
                        claim("Liability may be capped to fees paid or payable during the prior six months, except for listed categories.", "six (6) months before the event giving rise to liability", sources),
                        claim("Excluded services may create scope gaps unless added by change order.", "The Services do not include custom enterprise resource planning development", sources),
                        claim("Governing law and venue are fixed in Columbia/Ashton County.", "laws of the State of Columbia", sources)
                )
        );
    }

    private RiskReport buildPrivacyPolicy(List<DocumentChunk> sources) {
        return new RiskReport(
                "The privacy policy explains what customer and usage data is collected, limits use to service delivery and security, and defines retention and deletion timelines.",
                List.of(
                        claim("The policy collects account profile details and device diagnostics to operate the service.", "account profile details, device diagnostics", sources),
                        claim("Personal data is retained for twenty-four (24) months unless legal hold applies.", "twenty-four (24) months", sources),
                        claim("Users can request deletion through a verified privacy request process.", "request deletion through privacy@brightleaf.example", sources)
                ),
                List.of(
                        claim("The company must notify users before materially changing data practices.", "materially change this policy", sources),
                        claim("Security logs must be monitored for unauthorized access patterns.", "monitor security logs for unauthorized access", sources)
                ),
                List.of(
                        claim("Data may not be sold to third-party advertisers.", "do not sell personal data to third-party advertisers", sources),
                        claim("Access to raw support transcripts is restricted to trained support leads.", "raw support transcripts is restricted to trained support leads", sources)
                ),
                List.of(
                        claim("Regulatory requests can pause deletion timelines.", "legal hold requests may suspend deletion", sources),
                        claim("Policy updates take effect thirty (30) days after notice.", "thirty (30) days before new terms take effect", sources)
                ),
                List.of(
                        claim("Delayed privacy request handling could create compliance exposure.", "respond to verified requests within forty-five (45) days", sources),
                        claim("Retention exceptions for legal hold require clear documentation.", "legal hold requests may suspend deletion", sources)
                )
        );
    }

    private RiskReport buildEmploymentPolicy(List<DocumentChunk> sources) {
        return new RiskReport(
                "The employment policy defines attendance, conduct, disciplinary process, and manager approvals for leave, remote work, and conflicts of interest.",
                List.of(
                        claim("Employees must complete annual conduct training by March 31.", "annual conduct training by March 31", sources),
                        claim("Timesheets must be submitted by Monday 10:00 AM local time, and payroll adjustments depend on approved timesheets.", "Monday 10:00 AM local time", sources),
                        claim("Unauthorized overtime may result in corrective action.", "unauthorized overtime may result in corrective action", sources)
                ),
                List.of(
                        claim("Managers must acknowledge leave requests within five (5) business days.", "acknowledge leave requests within five (5) business days", sources),
                        claim("Employees must disclose conflicts of interest in writing.", "disclose conflicts of interest in writing", sources)
                ),
                List.of(
                        claim("Sharing confidential customer data on personal devices is prohibited.", "prohibited from storing confidential customer data on personal devices", sources),
                        claim("Remote work from unapproved countries is not allowed.", "remote work from unapproved countries is not allowed", sources)
                ),
                List.of(
                        claim("Repeated attendance violations can trigger progressive discipline up to termination.", "progressive discipline up to termination", sources),
                        claim("Policy violations involving harassment can result in immediate termination.", "harassment may result in immediate termination", sources)
                ),
                List.of(
                        claim("Late manager approvals may delay payroll adjustments when timesheets are late; payroll adjustments depend on approved timesheets.", "payroll adjustments depend on approved timesheets", sources),
                        claim("Cross-border remote work restrictions may impact staffing continuity.", "remote work from unapproved countries is not allowed", sources)
                )
        );
    }

    private RiskReport buildCampusStudentPolicy(List<DocumentChunk> sources) {
        return new RiskReport(
                "The campus student policy sets conduct standards, reporting deadlines, disciplinary steps, and accommodations for academic and residential settings.",
                List.of(
                        claim("Students must report safety incidents within twenty-four (24) hours.", "report safety incidents within twenty-four (24) hours", sources),
                        claim("Residence hall quiet hours run from 10:00 PM to 7:00 AM.", "quiet hours run from 10:00 PM to 7:00 AM", sources),
                        claim("Appeals must be filed within seven (7) calendar days.", "appeals must be filed within seven (7) calendar days", sources)
                ),
                List.of(
                        claim("The campus must provide accommodation review meetings within ten (10) business days.", "accommodation review meetings within ten (10) business days", sources),
                        claim("Students are expected to follow digital use and anti-harassment standards.", "anti-harassment standards apply to campus and online spaces", sources)
                ),
                List.of(
                        claim("Unauthorized access to labs or residence facilities is prohibited.", "unauthorized access to labs or residence facilities is prohibited", sources),
                        claim("Retaliation against reporting parties is prohibited.", "retaliation against reporting parties is prohibited", sources)
                ),
                List.of(
                        claim("Repeated major misconduct can lead to suspension or expulsion.", "major misconduct can lead to suspension or expulsion", sources),
                        claim("Failure to comply with sanctions can escalate penalties.", "failure to comply with sanctions may escalate penalties", sources)
                ),
                List.of(
                        claim("Short incident-reporting windows may reduce reporting completion.", "within twenty-four (24) hours", sources),
                        claim("Appeal deadlines may be missed without prompt notice.", "within seven (7) calendar days", sources)
                )
        );
    }

    private CitedClaim claim(String text, String anchor, List<DocumentChunk> sources) {
        return new CitedClaim(text, List.of(sourceIdFor(anchor, sources)), false);
    }

    private UUID sourceIdFor(String anchor, List<DocumentChunk> sources) {
        String normalizedAnchor = anchor.toLowerCase();
        for (DocumentChunk source : sources) {
            if (source.getTextContent() != null && source.getTextContent().toLowerCase().contains(normalizedAnchor)) {
                return source.getId();
            }
        }
        throw new SampleReportException("The built-in sample report could not be generated because a required source anchor was not found.");
    }
}
