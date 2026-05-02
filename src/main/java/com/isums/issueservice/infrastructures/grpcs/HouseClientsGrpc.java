package com.isums.issueservice.infrastructures.grpcs;

import com.isums.houseservice.grpc.GetHouseRequest;
import com.isums.houseservice.grpc.HouseResponse;
import com.isums.houseservice.grpc.HouseServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HouseClientsGrpc {
    private final HouseServiceGrpc.HouseServiceBlockingStub houseStub;

    public HouseResponse getHouseById(UUID houseId) {
        GetHouseRequest request = GetHouseRequest.newBuilder()
                .setHouseId(houseId.toString())
                .build();
        return houseStub.getHouseById(request);
    }
}
