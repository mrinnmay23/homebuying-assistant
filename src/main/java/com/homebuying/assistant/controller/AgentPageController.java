package com.homebuying.assistant.controller;

import com.homebuying.assistant.model.Agent;
import com.homebuying.assistant.service.AgentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class AgentPageController {

    private final AgentService service;

    public AgentPageController(AgentService service) {
        this.service = service;
    }

//    @GetMapping("/agent")
//    public String agentPage(
//            @RequestParam(name="id", required=false) Long id,
//            Model model
//    ) {
//        if (id != null) {
//            Agent agent = service.findById(id);
//            model.addAttribute("agent", agent);
//        }
//        return "agent";   // resolves to src/main/resources/templates/agent.html
//    }

    @GetMapping("/agent")
    public String agentPage(
            @RequestParam(name="id",    required=false) Long id,
            @RequestParam(name="name",  required=false) String name,
            Model model
    ) {
        // always populate the full list
        List<Agent> all = service.findAll();
        model.addAttribute("agents", all);

        // name this the same attribute your template expects
        if (id != null) {
            model.addAttribute("agent", service.findById(id));
        } else if (name != null && !name.isBlank()) {
            List<Agent> matches = service.findByNameContaining(name);
            if (!matches.isEmpty()) {
                model.addAttribute("agent", matches.get(0));
            }
            model.addAttribute("nameQuery", name);
        }
        return "agent";  // Thymeleaf template agent.html
    }

}

