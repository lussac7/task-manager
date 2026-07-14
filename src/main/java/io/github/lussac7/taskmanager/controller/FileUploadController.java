/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.dto.AppResponse;
import io.github.lussac7.taskmanager.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Handles file uploads and downloads for task attachments.
 *
 * <p>Files are stored on the server's filesystem (configured via
 * {@code app.upload.dir}). Only the filename is stored in the database
 * (in the {@code attachments} column of the {@code tasks} table).</p>
 */
@RestController
@RequestMapping("/api/tasks")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final TaskRepository taskRepository;

    // The folder where uploaded files are stored on disk.
    // Default: ./uploads (relative to the application's working directory).
    // Configure via app.upload.dir in application.yml.
    private final Path uploadDir;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================
    // When the application starts, we create the upload directory if it
    // doesn't already exist. If creation fails, the app fails to start
    // (no point running without a place to store files).

    public FileUploadController(TaskRepository taskRepository,
                                @Value("${app.upload.dir:./uploads}") String uploadPath) {
        this.taskRepository = taskRepository;
        // Resolve the upload path to an absolute, normalized path.
        // Example: "./uploads" → "/projects/java/task-manager/uploads"
        this.uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            // Create the directory (and any parent directories) if needed.
            // If it already exists, this call does nothing.
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + uploadDir, e);
        }
    }

    // =========================================================================
    // UPLOAD: POST /api/tasks/{id}/attachments
    // =========================================================================
    // The frontend sends a file via FormData (multipart/form-data).
    // Spring's MultipartFile handles the parsing automatically.

    /**
     * Uploads a file attachment to a task.
     *
     * @param id   the task ID to attach the file to
     * @param file the uploaded file (from a multipart form)
     * @return 200 with file details, or 500 if the upload fails
     */
    @PostMapping("/{id}/attachments")
    public ResponseEntity<AppResponse<Map<String, String>>> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        // Find the task — throw if it doesn't exist
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));

        // -----------------------------------------------------------------
        // Generate a safe, unique filename
        // -----------------------------------------------------------------
        // Why UUID? If two users upload "report.pdf", we don't want the
        // second one to overwrite the first. UUID guarantees uniqueness.
        // We keep the original extension so the file type is preserved.
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID().toString() + extension;

        try {
            // --- Save the file to disk ---
            Path targetPath = uploadDir.resolve(storedFilename);
            Files.copy(file.getInputStream(), targetPath);

            // --- Update the task's attachment list in the database ---
            // Attachments are stored as a comma-separated string of filenames.
            // Example: "abc123.pdf,def456.jpg"
            String currentAttachments = task.getAttachments();
            String updatedAttachments = (currentAttachments == null || currentAttachments.isEmpty())
                    ? storedFilename                         // First attachment
                    : currentAttachments + "," + storedFilename;  // Append to existing
            task.setAttachments(updatedAttachments);
            taskRepository.save(task);

            log.info("File uploaded for task {}: {} → {}", id, originalFilename, storedFilename);

            // Return the file info so the frontend can display it
            Map<String, String> response = Map.of(
                    "originalName", originalFilename != null ? originalFilename : "unknown",
                    "storedName", storedFilename,
                    "taskId", id.toString()
            );

            return ResponseEntity.ok(AppResponse.success("File uploaded", response));
        } catch (IOException e) {
            log.error("Failed to upload file for task {}", id, e);
            return ResponseEntity.internalServerError()
                    .body(AppResponse.error("Failed to upload file: " + e.getMessage()));
        }
    }

    // =========================================================================
    // DOWNLOAD: GET /api/tasks/-id-/attachments/filename
    // =========================================================================
    // The frontend creates a download link using the stored filename.
    // Clicking it triggers this endpoint, which reads the file from disk
    // and sends it to the browser.

    /**
     * Downloads an attached file by its stored filename.
     *
     * @param id       the task ID
     * @param filename the stored filename (UUID + extension)
     * @return the file for download, or 404 if not found
     */
    @GetMapping("/{id}/attachments/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable String filename) {

        // --- Security check: make sure this file belongs to this task ---
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));

        // Verify the filename is in the task's attachment list.
        // This prevents users from guessing filenames to download arbitrary files.
        if (task.getAttachments() == null || !task.getAttachments().contains(filename)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // --- Read the file from disk ---
            Path filePath = uploadDir.resolve(filename).normalize();
            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Send the file to the browser with a download header.
                // "attachment" means the browser will download it instead of
                // trying to display it inline.
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .body(resource);
            }
        } catch (Exception e) {
            log.error("Failed to read file: {}", filename, e);
        }

        return ResponseEntity.notFound().build();
    }
}