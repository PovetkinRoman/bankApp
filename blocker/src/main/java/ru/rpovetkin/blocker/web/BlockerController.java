package ru.rpovetkin.blocker.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.blocker.dto.TransferCheckRequest;
import ru.rpovetkin.blocker.dto.TransferCheckResponse;
import ru.rpovetkin.blocker.service.BlockerService;

@RestController
@RequestMapping("/api/blocker")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Для взаимодействия между модулями
public class BlockerController {
    
    private final BlockerService blockerService;
    
    /**
     * Проверить перевод на подозрительность
     */
    @PostMapping("/check-transfer")
    public ResponseEntity<TransferCheckResponse> checkTransfer(@RequestBody TransferCheckRequest request) {
        log.info("Received transfer check request from {} to {} for {} {}", 
                request.getFromUser(), request.getToUser(), 
                request.getAmount(), request.getCurrency());
        
        try {
            TransferCheckResponse response = blockerService.checkTransfer(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking transfer [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            
            // В случае ошибки - разрешаем перевод
            TransferCheckResponse errorResponse = TransferCheckResponse.builder()
                    .blocked(false)
                    .reason("Blocker service error - transfer approved")
                    .riskLevel("UNKNOWN")
                    .checkId("ERROR-" + System.currentTimeMillis())
                    .build();
            
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * Проверка работоспособности сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Blocker service is running");
    }
}
