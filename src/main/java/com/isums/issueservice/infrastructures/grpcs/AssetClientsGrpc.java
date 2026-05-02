package com.isums.issueservice.infrastructures.grpcs;

import com.isums.assetservice.grpc.AssetItemDto;
import com.isums.assetservice.grpc.AssetServiceGrpc;
import com.isums.assetservice.grpc.GetAssetItemsByHouseIdRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AssetClientsGrpc {
    private final AssetServiceGrpc.AssetServiceBlockingStub assetStub;

    public List<AssetItemDto> getAssetItemsByHouseId(UUID houseId) {
        GetAssetItemsByHouseIdRequest request = GetAssetItemsByHouseIdRequest.newBuilder()
                .setHouseId(houseId.toString())
                .build();
        return assetStub.getAssetItemsByHouseId(request).getAssetItemsList();
    }
}
