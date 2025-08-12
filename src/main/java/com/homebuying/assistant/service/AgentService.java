package com.homebuying.assistant.service;

import com.homebuying.assistant.model.Agent;
import com.homebuying.assistant.repository.AgentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService {
    private final AgentRepository repo;
    public AgentService(AgentRepository repo){ this.repo = repo; }
    public Agent findById(Long id){ return repo.findById(id).orElseThrow(); }

    public List<Agent> findByNameContaining(String name) {
        return repo.findByNameContainingIgnoreCase(name);
    }
    public List<Agent> findAll() {
        return repo.findAll();
    }
}

