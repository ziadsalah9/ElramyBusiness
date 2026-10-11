package Elramy.Group.MafroshartElramyz.repository;

import Elramy.Group.MafroshartElramyz.models.ProductDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductDraftRepository extends JpaRepository<ProductDraft, Long> {

    List<ProductDraft> findAllByOrderByCreatedAtDescIdDesc();
}
