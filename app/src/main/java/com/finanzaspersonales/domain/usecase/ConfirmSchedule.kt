package com.finanzaspersonales.domain.usecase
import com.finanzaspersonales.domain.repository.FinanceRepository
import java.time.LocalDate
import javax.inject.Inject
class ConfirmSchedule @Inject constructor(private val repository: FinanceRepository) {
 suspend operator fun invoke(id: String, due: LocalDate, cents: Long, date: LocalDate) = repository.confirm(id, due, cents, date)
}
