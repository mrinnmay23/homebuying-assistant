package com.homebuying.assistant.repository;

import com.homebuying.assistant.model.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@Repository
public interface AgentRepository extends JpaRepository<Agent,Long> { }


