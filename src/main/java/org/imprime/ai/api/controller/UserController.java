package org.imprime.ai.api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.response.BaseResponse;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.FullUserDTO;
import org.imprime.ai.api.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        FullUserDTO user = userService.getFullUserDetails();
        return BaseResponse.ok(user);
    }

    @GetMapping("/address")
    public ResponseEntity<BaseResponse<List<AddressDTO>>> getAddresses(@RequestParam(required = false, defaultValue = "false") boolean company, @RequestParam(required = false, defaultValue = "0") Integer page, @RequestParam(required = false, defaultValue = "10") Integer size) {
        List<AddressDTO> addresses = userService.listAddresses(company, page, size);
        return BaseResponse.ok(addresses);
    }
}
