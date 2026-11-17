package dev.shortline;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventRepository extends JpaRepository<ClickEvent, String> {
    long countByCode(String code);
}
