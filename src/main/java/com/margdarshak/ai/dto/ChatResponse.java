package com.margdarshak.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.margdarshak.ai.model.Intent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private Intent intent;
    private Object result;
}
