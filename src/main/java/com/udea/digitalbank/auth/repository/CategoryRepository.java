package com.udea.digitalbank.auth.repository;

import com.udea.digitalbank.auth.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Short> {
}
