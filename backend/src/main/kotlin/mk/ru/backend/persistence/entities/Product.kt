package mk.ru.backend.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import java.time.LocalDate
import java.util.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.annotations.UuidGenerator

@Entity
data class Product(
    @Id
    @UuidGenerator
    var id: UUID? = null,
    @Column(nullable = false)
    var name: String? = null,
    @Column(nullable = true)
    var description: String? = null,
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    var registrationDate: LocalDate? = LocalDate.now(),
    @Column(nullable = false)
    var deleted: Boolean? = false,
    @Column(nullable = false)
    var selling: Boolean? = false,
    @OneToMany(targetEntity = PriceHistory::class, mappedBy = "product")
    @Fetch(FetchMode.JOIN)
    var priceHistory: List<PriceHistory>? = null,
    @ManyToOne(targetEntity = Category::class)
    @Fetch(FetchMode.JOIN)
    @JoinColumn(nullable = false)
    var category: Category? = null,
    @ManyToOne(targetEntity = AppUser::class, fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    var owner: AppUser? = null,
)