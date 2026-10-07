package group5.example.sportCenter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import group5.example.sportCenter.entity.Sport;

public interface SportRepository
        extends JpaRepository<Sport, Integer> {

    boolean existsByNameIgnoreCase(String name);

    List<Sport> findByStatus(String status);
}