package com.workspace.storm.event.web;

import com.workspace.storm.event.db.entity.SourceStateEntity;
import com.workspace.storm.event.db.repository.SourceStateRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sources")
public class SourcesAdminController {

    private final SourceStateRepository sourceStateRepository;

    public SourcesAdminController(SourceStateRepository sourceStateRepository) {
        this.sourceStateRepository = sourceStateRepository;
    }

    @GetMapping
    public List<SourceStateEntity> list() {
        return sourceStateRepository.findAll();
    }

    @PostMapping("/{name}/enable")
    public ResponseEntity<String> enable(@PathVariable String name) {
        SourceStateEntity entity = new SourceStateEntity();
        entity.setSource(name);
        entity.setEnabled(true);
        entity.setDraining(false);
        entity.setRampUntil(Instant.now().plusSeconds(30));
        entity.setUpdatedAt(Instant.now());
        sourceStateRepository.upsert(entity);
        return ResponseEntity.ok("enabled " + name);
    }

    @PostMapping("/{name}/disable")
    public ResponseEntity<String> disable(@PathVariable String name) {
        SourceStateEntity entity = new SourceStateEntity();
        entity.setSource(name);
        entity.setEnabled(false);
        entity.setDraining(true);
        entity.setUpdatedAt(Instant.now());
        sourceStateRepository.upsert(entity);
        return ResponseEntity.ok("disabled " + name);
    }
}
