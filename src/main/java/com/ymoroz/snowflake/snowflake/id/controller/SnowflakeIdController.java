package com.ymoroz.snowflake.snowflake.id.controller;

import com.ymoroz.snowflake.snowflake.id.response.GeneratedIdResponse;
import com.ymoroz.snowflake.snowflake.id.service.SnowflakeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SnowflakeIdController {
    
    private final SnowflakeService snowflakeService;
    
    public SnowflakeIdController(SnowflakeService snowflakeService) {
        this.snowflakeService = snowflakeService;
    }
    
    @GetMapping("snowflake/generateId")
    public GeneratedIdResponse generateId() {
        long id = snowflakeService.nextId();
        return new GeneratedIdResponse(id);
    }
}
