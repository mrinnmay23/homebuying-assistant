package com.homebuying.assistant.controller;

import com.homebuying.assistant.model.Agent;
import com.homebuying.assistant.service.AgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService service;
    public AgentController(AgentService service){ this.service = service; }

    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        return service.findById(id);
    }
}

