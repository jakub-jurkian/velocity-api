package com.velocity.api.common.web;

import com.velocity.api.common.exception.InvalidSortException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;


// The fields a paginated endpoint lets clients sort by.
// Spring Data accepts any entity property in ?sort=, including ones that should never
// be an ordering oracle (a password hash) and ones that do not exist (which fail as a 500 deep
// inside the query). Each list endpoint declares its own allow-list and checks the request
// against it before it reaches the repository.
public record SortableFields(Set<String> allowed) {

    public static SortableFields of(String... fields) {
        return new SortableFields(Set.of(fields));
    }

    public Pageable check(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), new TreeSet<>(allowed));
            }
        }
        return pageable;
    }
}
