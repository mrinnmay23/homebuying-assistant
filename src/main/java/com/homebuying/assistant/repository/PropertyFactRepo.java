package com.homebuying.assistant.repository;


import com.homebuying.assistant.model.PropertyFact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PropertyFactRepo extends JpaRepository<PropertyFact, Long> {
    @Query("""
        select p from PropertyFact p
        where (:beds     is null or p.bedrooms >= :beds)
          and (:maxPrice is null or (p.price is not null and p.price <= :maxPrice))
          and (:city     is null or lower(p.city) like lower(concat('%', :city, '%')))
        order by
          case when p.price is null then 1 else 0 end,
          p.price asc,
          p.bedrooms desc
        """)
    List<PropertyFact> search(@Param("beds") Integer beds,
                              @Param("maxPrice") Integer maxPrice,
                              @Param("city") String city,
                              Pageable pageable);
}
