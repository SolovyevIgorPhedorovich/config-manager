package com.uniikm.configmanager.device.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uniikm.configmanager.common.crypto.SecretCipher;
import com.uniikm.configmanager.device.dto.ScanCredentialDto;
import com.uniikm.configmanager.device.dto.ScanCredentialRequest;
import com.uniikm.configmanager.device.model.ScanCredential;
import com.uniikm.configmanager.device.repository.ScanCredentialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScanCredentialService {

    private final ScanCredentialRepository repository;
    private final SecretCipher cipher;

    /** Расшифрованные креды для запуска опроса (внутреннее использование). */
    public record ResolvedCreds(
            String sshUsername, String sshPassword,
            String winrmUsername, String winrmPassword) {}

    public List<ScanCredentialDto> getAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public ScanCredentialDto create(ScanCredentialRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Имя профиля обязательно");
        }
        if (repository.existsByName(req.name())) {
            throw new IllegalArgumentException("Профиль с именем '" + req.name() + "' уже существует");
        }
        ScanCredential entity = ScanCredential.builder()
                .name(req.name().trim())
                .domain(req.domain())
                .sshUsername(req.sshUsername())
                .sshPassword(cipher.encrypt(req.sshPassword()))
                .winrmUsername(req.winrmUsername())
                .winrmPassword(cipher.encrypt(req.winrmPassword()))
                .build();
        return toDto(repository.save(entity));
    }

    @Transactional
    public ScanCredentialDto update(Long id, ScanCredentialRequest req) {
        ScanCredential entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Профиль не найден: " + id));
        if (req.name() != null && !req.name().isBlank()) entity.setName(req.name().trim());
        entity.setDomain(req.domain());
        entity.setSshUsername(req.sshUsername());
        entity.setWinrmUsername(req.winrmUsername());
        // Пустой пароль в запросе = «не менять» (чтобы не требовать повторного ввода)
        if (req.sshPassword() != null && !req.sshPassword().isBlank()) {
            entity.setSshPassword(cipher.encrypt(req.sshPassword()));
        }
        if (req.winrmPassword() != null && !req.winrmPassword().isBlank()) {
            entity.setWinrmPassword(cipher.encrypt(req.winrmPassword()));
        }
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** Возвращает расшифрованные креды профиля для запуска скана. */
    public ResolvedCreds resolve(Long id) {
        ScanCredential e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Профиль не найден: " + id));
        return new ResolvedCreds(
                e.getSshUsername(), cipher.decrypt(e.getSshPassword()),
                qualifyWithDomain(e.getDomain(), e.getWinrmUsername()),
                cipher.decrypt(e.getWinrmPassword()));
    }

    /**
     * Если задан домен, а имя пользователя ещё не доменное (нет обратного слэша и '@'),
     * приводим к виду DOMAIN-backslash-user для WinRM.
     */
    private String qualifyWithDomain(String domain, String username) {
        if (username == null || username.isBlank()) return username;
        if (domain == null || domain.isBlank()) return username;
        if (username.contains("\\") || username.contains("@")) return username;
        return domain + "\\" + username;
    }

    private ScanCredentialDto toDto(ScanCredential e) {
        return new ScanCredentialDto(
                e.getId(),
                e.getName(),
                e.getDomain(),
                e.getSshUsername(),
                e.getSshPassword() != null && !e.getSshPassword().isBlank(),
                e.getWinrmUsername(),
                e.getWinrmPassword() != null && !e.getWinrmPassword().isBlank(),
                e.getCreatedAt());
    }
}
