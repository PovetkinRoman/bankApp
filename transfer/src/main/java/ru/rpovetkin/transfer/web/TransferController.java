package ru.rpovetkin.transfer.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.rpovetkin.transfer.dto.TransferRequest;
import ru.rpovetkin.transfer.dto.TransferResponse;
import ru.rpovetkin.transfer.service.TransferService;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Для взаимодействия между модулями
public class TransferController {
    
    private final TransferService transferService;
    
    /**
     * Выполнить перевод между пользователями
     */
    @PostMapping("/execute")
    public ResponseEntity<TransferResponse> executeTransfer(@RequestBody TransferRequest request) {
        log.info("Received transfer request: {} -> {} for {} {}", 
                request.getFromUser(), request.getToUser(), 
                request.getAmount(), request.getCurrency());
        
        TransferResponse response = transferService.processTransfer(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Проверка работоспособности сервиса
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Transfer service is running");
    }
}
