package com.example.web.controller;

import com.example.web.dto.SchoolTermResponse;
import com.example.web.dto.UpdateTermRequest;
import com.example.web.entity.SchoolTermEntity;
import com.example.web.repository.SchoolTermRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current term/period label (e.g. "Fall 2026"), shown in the header for
 * every role and editable by ADMIN in Settings. Purely organizational
 * metadata - never read by the solver.
 *
 * Read is mounted at /api/term (open to any authenticated role, like other
 * GETs); write is mounted at /api/admin/term so SecurityConfig's existing
 * /api/admin/** rule restricts it to ADMIN without needing a new rule.
 */
@RestController
public class SchoolTermController {

    @Autowired
    private SchoolTermRepository termRepository;

    @GetMapping("/api/term")
    public SchoolTermResponse getTerm() {
        return new SchoolTermResponse(currentTerm());
    }

    @PutMapping("/api/admin/term")
    public SchoolTermResponse updateTerm(@Valid @RequestBody UpdateTermRequest request) {
        SchoolTermEntity term = currentTerm();
        term.setLabel(request.getLabel());
        return new SchoolTermResponse(termRepository.save(term));
    }

    /** The migration seeds row id=1; this fallback only matters if that seed was somehow missed. */
    private SchoolTermEntity currentTerm() {
        return termRepository.findById(1).orElseGet(() -> new SchoolTermEntity(1, ""));
    }
}
