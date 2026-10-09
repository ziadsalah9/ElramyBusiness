package Elramy.Group.MafroshartElramyz.stocktake;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {

    List<Stocktake> findAllByOrderByCreatedAtDesc();

    Optional<Stocktake> findFirstByStatus(StocktakeStatus status);

    long countByCodeStartingWith(String prefix);

    List<Stocktake> findByStatusAndApprovedAtGreaterThanEqualAndApprovedAtLessThanOrderByApprovedAtDesc(
            StocktakeStatus status, java.time.LocalDateTime from, java.time.LocalDateTime to);
}
