package com.policyinsight.service;

import com.policyinsight.ai.dto.CitedClaim;
import com.policyinsight.ai.dto.RiskReport;
import com.policyinsight.model.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SampleAgreementReportBuilder {

    public RiskReport build(List<DocumentChunk> sources) {
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

    private CitedClaim claim(String text, String anchor, List<DocumentChunk> sources) {
        return new CitedClaim(text, List.of(sourceIdFor(anchor, sources)), false);
    }

    private UUID sourceIdFor(String anchor, List<DocumentChunk> sources) {
        for (DocumentChunk source : sources) {
            if (source.getTextContent() != null && source.getTextContent().contains(anchor)) {
                return source.getId();
            }
        }
        throw new SampleReportException("The built-in sample report could not be generated because a required source anchor was not found.");
    }
}
