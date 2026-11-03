package com.bookbridge.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.dto.PublicProfileResponse;
import com.bookbridge.dto.ReviewResponse;
import com.bookbridge.service.ProfileService;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}/profile")
    public PublicProfileResponse profile(@PathVariable String userId) {
        return profileService.get(userId);
    }

    @GetMapping("/{userId}/reviews")
    public List<ReviewResponse> reviews(@PathVariable String userId) {
        return profileService.reviews(userId);
    }
}
