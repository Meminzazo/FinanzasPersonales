package com.finanzaspersonales.domain.repository
import com.finanzaspersonales.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
interface FinanceRepository {
 val ledger: Flow<Ledger>
 suspend fun seed()
 suspend fun category(c: Category)
 suspend fun movement(m: Movement)
 suspend fun deleteMovement(id: String)
 suspend fun schedule(s: Schedule)
 suspend fun deleteSchedule(id: String)
 suspend fun confirm(id: String, due: LocalDate, cents: Long, date: LocalDate)
 suspend fun goal(g: Goal)
 suspend fun saving(s: Saving)
 suspend fun account(a: CreditAccount)
 suspend fun payment(p: DebtPayment)
 suspend fun deletePayment(id: String)
 suspend fun createPlan(p: InstallmentPlan)
 suspend fun updatePlan(p: InstallmentPlan)
 suspend fun deletePlan(id: String)
 suspend fun confirmInstallment(id: String, number: Int, date: LocalDate)
 suspend fun snapshot(): Ledger
 suspend fun restore(l: Ledger)
}
