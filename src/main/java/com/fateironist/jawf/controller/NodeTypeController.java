package com.fateironist.jawf.controller;

import com.fateironist.jawf.model.NodeTypeInfo;
import com.fateironist.jawf.service.NodeTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 节点类型 REST 控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/node-types")
public class NodeTypeController {

    private final NodeTypeService nodeTypeService;

    public NodeTypeController(NodeTypeService nodeTypeService) {
        this.nodeTypeService = nodeTypeService;
    }

    /**
     * 获取所有可用的节点类型。
     */
    @GetMapping
    public ResponseEntity<List<NodeTypeInfo>> getAllNodeTypes() {
        return ResponseEntity.ok(nodeTypeService.getAllNodeTypes());
    }

    /**
     * 获取指定类型的节点信息。
     */
    @GetMapping("/{type}")
    public ResponseEntity<NodeTypeInfo> getNodeType(@PathVariable String type) {
        NodeTypeInfo nodeType = nodeTypeService.getNodeType(type);
        if (nodeType == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(nodeType);
    }

    /**
     * 检查节点类型是否有效。
     */
    @GetMapping("/{type}/valid")
    public ResponseEntity<Boolean> isValidNodeType(@PathVariable String type) {
        return ResponseEntity.ok(nodeTypeService.isValidNodeType(type));
    }
}
