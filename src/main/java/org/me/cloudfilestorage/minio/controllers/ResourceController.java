package org.me.cloudfilestorage.minio.controllers;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.me.cloudfilestorage.minio.services.ResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/resource")
@AllArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping("/{*path}")
    public ResponseEntity<?> getResource(@RequestParam String path) throws Exception {
        return resourceService.findResource(path);
    }

    @DeleteMapping("/{*path}")
    public ResponseEntity<?> deleteResource(@RequestParam String path) throws Exception {
        return resourceService.deleteResource(path);
    }

    @GetMapping("/download/{*path}")
    public void downloadResource(@RequestParam String path, HttpServletResponse response) throws Exception {
        String fileName = path.substring(path.lastIndexOf("/")+1);
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.setContentType("application/octet-stream");
        resourceService.downloadResource(path, response.getOutputStream());

    }

    @GetMapping("/rename")
    public ResponseEntity<?> renameResource(@RequestParam String path, @RequestParam String path2) throws Exception {
        return resourceService.renameResource(path, path2);
    }
}

