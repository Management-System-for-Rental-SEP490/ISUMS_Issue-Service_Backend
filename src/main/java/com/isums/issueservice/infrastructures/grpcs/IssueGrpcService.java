package com.isums.issueservice.infrastructures.grpcs;

import com.isums.issueservice.domains.entities.IssueQuote;
import com.isums.issueservice.domains.entities.IssueTicket;
import com.isums.issueservice.domains.entities.QuoteItem;
import com.isums.issueservice.grpc.*;
import com.isums.issueservice.infrastructures.repositories.IssueQuoteRepository;
import com.isums.issueservice.infrastructures.repositories.IssueTicketRepository;
import com.isums.issueservice.infrastructures.repositories.QuoteItemRepository;
import com.isums.maintenanceservice.grpc.GetJobResponse;
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
    private final IssueTicketRepository issueTicketRepository;

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

    @Override
    @Transactional(readOnly = true)
    public void getLatestQuoteByReference(GetQuoteByReferenceRequest request,
                                          StreamObserver<QuoteFullResponse> responseObserver) {
        try {
            UUID referenceId;
            try {
                referenceId = UUID.fromString(request.getReferenceId());
            } catch (IllegalArgumentException e) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Invalid referenceId").asRuntimeException());
                return;
            }
            String referenceType = request.getReferenceType();
            if (referenceType == null || referenceType.isBlank()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("referenceType is required").asRuntimeException());
                return;
            }

            var opt = issueQuoteRepository
                    .findFirstByReferenceIdAndReferenceTypeOrderByCreatedAtDesc(referenceId, referenceType);

            if (opt.isEmpty()) {
                responseObserver.onNext(QuoteFullResponse.newBuilder().setFound(false).build());
                responseObserver.onCompleted();
                return;
            }

            IssueQuote quote = opt.get();
            QuoteFullResponse.Builder b = QuoteFullResponse.newBuilder()
                    .setFound(true)
                    .setId(quote.getId().toString())
                    .setReferenceId(quote.getReferenceId() != null ? quote.getReferenceId().toString() : "")
                    .setReferenceType(quote.getReferenceType() != null ? quote.getReferenceType() : "")
                    .setStaffId(quote.getStaffId() != null ? quote.getStaffId().toString() : "")
                    .setTotalPrice(quote.getTotalPrice() != null ? quote.getTotalPrice().toPlainString() : "0")
                    .setIsTenantFault(Boolean.TRUE.equals(quote.getIsTenantFault()))
                    .setStatus(quote.getStatus() != null ? quote.getStatus().name() : "")
                    .setCreatedAtEpochMilli(quote.getCreatedAt() != null ? quote.getCreatedAt().toEpochMilli() : 0L);

            if (quote.getItems() != null) {
                for (QuoteItem it : quote.getItems()) {
                    b.addItems(QuoteFullItem.newBuilder()
                            .setId(it.getId() != null ? it.getId().toString() : "")
                            .setItemName(it.getItemName() != null ? it.getItemName() : "")
                            .setDescription(it.getDescription() != null ? it.getDescription() : "")
                            .setPrice(it.getPrice() != null ? it.getPrice().toPlainString() : "0")
                            .build());
                }
            }

            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("[IssueGrpc] getLatestQuoteByReference failed referenceId={} type={}: {}",
                    request.getReferenceId(), request.getReferenceType(), e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getHouseByIssueId(GetIssueRequest request, StreamObserver<GetIssueResponse> responseObserver) {
        try {
            UUID id;
            try {
                id = UUID.fromString(request.getId());
            } catch (IllegalArgumentException e) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Invalid jobId format")
                                .asRuntimeException()
                );
                return;
            }

            IssueTicket ticket = issueTicketRepository.findById(id)
                    .orElseThrow(() ->
                            Status.NOT_FOUND
                                    .withDescription("ticketId not found: " + id)
                                    .asRuntimeException()
                    );

            GetIssueResponse response = GetIssueResponse.newBuilder()
                    .setId(id.toString())
                    .setHouseId(ticket.getHouseId().toString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("[IssueGrpc] getHouseByIssueId failed ticketId={}: {}",
                    request.getId(), e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}