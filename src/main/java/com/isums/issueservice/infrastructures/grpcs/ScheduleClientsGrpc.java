package com.isums.issueservice.infrastructures.grpcs;

import com.isums.scheduleservice.grpc.AutoAssignRequest;
import com.isums.scheduleservice.grpc.AutoAssignResponse;
import com.isums.scheduleservice.grpc.ScheduleServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleClientsGrpc {

    private final ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleStub;

    public AutoAssignResponse autoAssign(UUID referenceId, UUID tenantId, UUID houseId, String referenceType) {
        AutoAssignRequest req = AutoAssignRequest.newBuilder()
                .setReferenceId(referenceId.toString())
                .setTenantId(tenantId == null ? "" : tenantId.toString())
                .setHouseId(houseId == null ? "" : houseId.toString())
                .setReferenceType(referenceType)
                .build();
        try {
            AutoAssignResponse resp = scheduleStub.autoAssign(req);
            log.info("[ScheduleGrpc] AutoAssign result referenceId={} slotId={} staffId={} status={}",
                    referenceId, resp.getSlotId(), resp.getStaffId(), resp.getStatus());
            return resp;
        } catch (Exception e) {
            log.warn("[ScheduleGrpc] AutoAssign call failed referenceId={}: {}", referenceId, e.getMessage());
            return null;
        }
    }
}
