package com.midaas.moonwalk.repository;

import com.midaas.moonwalk.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {
}
