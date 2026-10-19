package com.capstone.steam.cover;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.capstone.steam.game.dto.GameDetailResponse;
import com.capstone.steam.security.AppUserPrincipal;

/**
 * Subida de portadas. Las reglas de URL de SecurityConfig ya limitan {@code /api/developer/**}
 * a developers y {@code /api/admin/**} a admins; el estado solo pide sesión.
 */
@RestController
public class CoverController {

    private final CoverService covers;

    public CoverController(CoverService covers) {
        this.covers = covers;
    }

    @PostMapping(path = "/api/developer/games/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GameDetailResponse uploadAsDeveloper(@AuthenticationPrincipal AppUserPrincipal dev,
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return covers.uploadAsDeveloper(dev.getId(), id, file);
    }

    @PostMapping(path = "/api/admin/games/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GameDetailResponse uploadAsAdmin(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return covers.uploadAsAdmin(id, file);
    }

    /** El frontend lo consulta para mostrar (o no) el botón de subir portada. */
    @GetMapping("/api/uploads/covers/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", covers.enabled());
    }
}
