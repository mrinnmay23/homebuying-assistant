package com.homebuying.assistant.service;

import com.homebuying.assistant.model.Agent;
import com.homebuying.assistant.repository.AgentRepository;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
    private final AgentRepository repo;
    public AgentService(AgentRepository repo){ this.repo = repo; }
    public Agent findById(Long id){ return repo.findById(id).orElseThrow(); }
}

