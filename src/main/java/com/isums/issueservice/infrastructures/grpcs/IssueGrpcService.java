package com.isums.issueservice.infrastructures.grpcs;

import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.QuoteItem;
import com.isums.issueservice.grpc.GetQuoteByIdRequest;
import com.isums.issueservice.grpc.IssueServiceGrpc;
import com.isums.issueservice.grpc.QuoteDetailResponse;
import com.isums.issueservice.grpc.QuoteResponse;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.QuoteItemRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueGrpcService extends IssueServiceGrpc.IssueServiceImplBase {

    private final IssueQuoteRepository issueQuoteRepository;
    private final QuoteItemRepository quoteItemRepository;

    @Override
    @Transactional(readOnly = true)
    public void getQuoteById(GetQuoteByIdRequest request,
                             StreamObserver<QuoteResponse> responseObserver) {
        try {
            UUID quoteId = UUID.fromString(request.getQuoteId());

            IssueQuote quote = issueQuoteRepository.findById(quoteId)
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("Quote not found: " + quoteId)
                            .asRuntimeException());

            String tenantId = quote.getIssueTicket().getTenantId() != null
                    ? quote.getIssueTicket().getTenantId().toString()
                    : "";

            String issueId = quote.getIssueTicket().getId() != null
                    ? quote.getIssueTicket().getId().toString()
                    : "";

            QuoteResponse response = QuoteResponse.newBuilder()
                    .setId(quote.getId().toString())
                    .setIssueId(issueId)
                    .setTenantId(tenantId)
                    .setTotalPrice(quote.getTotalPrice().toPlainString())
                    .setStatus(quote.getStatus().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("[IssueGrpc] getQuoteById failed quoteId={}: {}",
                    request.getQuoteId(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void getQuoteDetail(GetQuoteByIdRequest request, StreamObserver<QuoteDetailResponse> responseObserver) {
        try {

            UUID quoteId = UUID.fromString(request.getQuoteId());

            IssueQuote quote = issueQuoteRepository.findById(quoteId)
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("Quote not found: " + quoteId)
                            .asRuntimeException());

            List<QuoteItem> items = quoteItemRepository.findByQuoteId(quoteId);

            String tenantId = quote.getIssueTicket().getTenantId() != null
                    ? quote.getIssueTicket().getTenantId().toString()
                    : "";

            String issueId = quote.getIssueTicket().getId() != null
                    ? quote.getIssueTicket().getId().toString()
                    : "";

            QuoteDetailResponse.Builder builder = QuoteDetailResponse.newBuilder()
                    .setId(quote.getId().toString())
                    .setIssueId(issueId)
                    .setTenantId(tenantId)
                    .setTotalPrice(quote.getTotalPrice().toPlainString())
                    .setStatus(quote.getStatus().name());

            for (QuoteItem item : items) {
                builder.addItems(
                        com.isums.issueservice.grpc.QuoteItem.newBuilder()
                                .setId(item.getId().toString())
                                .setItemName(item.getItemName())
                                .setPrice(item.getPrice().doubleValue())
                                .build()
                );
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}