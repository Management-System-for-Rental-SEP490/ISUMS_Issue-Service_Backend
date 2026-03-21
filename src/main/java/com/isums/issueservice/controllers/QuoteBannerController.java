package com.isums.issueservice.controllers;

import com.isums.issueservice.domains.dtos.ApiResponse;
import com.isums.issueservice.domains.dtos.ApiResponses;
import com.isums.issueservice.domains.dtos.BannerDto;
import com.isums.issueservice.domains.dtos.CreateBannerRequest;
import com.isums.issueservice.infrastructures.abstracts.QuoteBannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues/banners")
@RequiredArgsConstructor
public class QuoteBannerController {
    private final QuoteBannerService quoteBannerService;

    @PostMapping
    public ApiResponse<BannerDto> create(@RequestBody CreateBannerRequest req) {
        BannerDto res = quoteBannerService.create(req);
        return ApiResponses.created(res,"create banner successfully");
    }

    @GetMapping
    public ApiResponse<List<BannerDto>> getAll(){
        List<BannerDto> res = quoteBannerService.getAll();
        return ApiResponses.ok(res,"get all banner successfully");
    }

    @PutMapping("/{id}/price")
    public ApiResponse<BannerDto> updatePrice(@PathVariable UUID id, @RequestParam BigDecimal price) {
        BannerDto res = quoteBannerService.updatePrice(id,price);
        return ApiResponses.ok(res,"update price for banner successfully");
    }
}
