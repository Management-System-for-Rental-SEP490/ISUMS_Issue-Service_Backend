package com.isums.issueservice.infrastructures.Grpcs;

import com.isums.userservice.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserClientsGrpc {
    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public UserResponse getUserIdAndRoleByKeyCloakId(String keycloakId) {
        GetUserIdAndRoleByKeyCloakIdRequest req = GetUserIdAndRoleByKeyCloakIdRequest.newBuilder().setKeycloakId(keycloakId).build();
        return stub.getUserIdAndRoleByKeyCloakId(req);
    }

    public UserResponse getUser(String userId) {
        GetUserByIdRequest req = GetUserByIdRequest.newBuilder().setUserId(userId).build();
        return stub.getUserById(req);
    }
}
