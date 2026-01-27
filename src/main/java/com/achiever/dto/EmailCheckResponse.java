package com.achiever.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmailCheckResponse {
    private boolean exists;
    private boolean hasPassword;
}