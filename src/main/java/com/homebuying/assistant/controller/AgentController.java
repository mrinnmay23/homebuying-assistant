package com.homebuying.assistant.controller;

import com.homebuying.assistant.model.Agent;
import com.homebuying.assistant.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService service;
    public AgentController(AgentService service){ this.service = service; }

    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping(params="name")
    public List<Agent> findByName(@RequestParam String name) {
        return service.findByNameContaining(name);
    }

    @GetMapping
    public List<Agent> findAll() {
        return service.findAll();
    }
}

