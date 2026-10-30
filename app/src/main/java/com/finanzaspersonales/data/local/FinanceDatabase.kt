package com.finanzaspersonales.data.local
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="categories") data class CategoryRow(@PrimaryKey val id: String, val name: String, val kind: String, val archived: Boolean)
@Entity(tableName="movements", indices=[Index(value=["occurrence"],unique=true)]) data class MovementRow(@PrimaryKey val id: String, val cents: Long, val categoryId: String, val date: String, val note: String, val occurrence: String?, val accountId: String? = null)
@Entity(tableName="schedules") data class ScheduleRow(@PrimaryKey val id: String, val title: String, val cents: Long, val categoryId: String, val due: String, val frequency: String, val anchorDay: Int, val accountId: String? = null, val paymentsTotal: Int? = null, val paymentsRemaining: Int? = null)
@Entity(tableName="goals") data class GoalRow(@PrimaryKey val id: String, val name: String, val target: Long, val deadline: String?)
@Entity(tableName="savings") data class SavingRow(@PrimaryKey val id: String, val goalId: String, val cents: Long, val date: String)
@Entity(tableName="credit_accounts") data class AccountRow(@PrimaryKey val id: String, val name: String, val type: String, val openingCents: Long, val openedOn: String, val archived: Boolean, val cutoffDay: Int? = null)
@Entity(tableName="debt_payments",indices=[Index(value=["planId","installment"],unique=true)]) data class PaymentRow(@PrimaryKey val id: String, val accountId: String, val cents: Long, val date: String, val planId: String? = null, val installment: Int? = null)
@Entity(tableName="installment_plans") data class PlanRow(@PrimaryKey val id:String,val title:String,val accountId:String,val totalCents:Long,val months:Int,val paidBefore:Int,val firstDue:String,val purchaseDate:String,val categoryId:String,val historical:Boolean,val includedInDebt:Boolean,val registeredOn:String,val amounts:String? = null)
@Dao interface FinanceDao {
 @Query("SELECT * FROM installment_plans") suspend fun plans(): List<PlanRow>
 @Upsert suspend fun plan(p:PlanRow)
 @Query("DELETE FROM installment_plans WHERE id=:id") suspend fun deletePlan(id:String)
 @Query("DELETE FROM debt_payments WHERE planId=:id") suspend fun clearPlanPayments(id:String)
 @Query("DELETE FROM installment_plans") suspend fun clearPlans()
 @Query("SELECT * FROM credit_accounts") suspend fun accounts(): List<AccountRow>
 @Query("SELECT * FROM debt_payments ORDER BY date DESC, id") suspend fun payments(): List<PaymentRow>
 @Upsert suspend fun account(a: AccountRow)
 @Upsert suspend fun payment(p: PaymentRow)
 @Query("DELETE FROM debt_payments WHERE id=:id") suspend fun deletePayment(id: String)
 @Query("DELETE FROM credit_accounts") suspend fun clearAccounts()
 @Query("DELETE FROM debt_payments") suspend fun clearPayments()
 @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryRow>
 @Query("SELECT * FROM movements ORDER BY date DESC, id") suspend fun movements(): List<MovementRow>
 @Query("SELECT * FROM schedules ORDER BY due") suspend fun schedules(): List<ScheduleRow>
 @Query("SELECT * FROM goals") suspend fun goals(): List<GoalRow>
 @Query("SELECT * FROM savings ORDER BY date DESC") suspend fun savings(): List<SavingRow>
 @Query("SELECT * FROM categories") fun observe(): Flow<List<CategoryRow>>
 @Upsert suspend fun category(c: CategoryRow)
 @Upsert suspend fun movement(m: MovementRow)
 @Upsert suspend fun schedule(s: ScheduleRow)
 @Upsert suspend fun goal(g: GoalRow)
 @Upsert suspend fun saving(s: SavingRow)
 @Query("DELETE FROM movements WHERE id=:id") suspend fun deleteMovement(id: String)
 @Query("DELETE FROM schedules WHERE id=:id") suspend fun deleteSchedule(id: String)
 @Query("DELETE FROM savings") suspend fun clearSavings()
 @Query("DELETE FROM movements") suspend fun clearMovements()
 @Query("DELETE FROM schedules") suspend fun clearSchedules()
 @Query("DELETE FROM goals") suspend fun clearGoals()
 @Query("DELETE FROM categories") suspend fun clearCategories()
}
@Database(entities=[CategoryRow::class,MovementRow::class,ScheduleRow::class,GoalRow::class,SavingRow::class,AccountRow::class,PaymentRow::class,PlanRow::class],version=6,autoMigrations=[AutoMigration(from=1,to=2),AutoMigration(from=2,to=3),AutoMigration(from=3,to=4),AutoMigration(from=4,to=5),AutoMigration(from=5,to=6)],exportSchema=true)
abstract class FinanceDatabase: RoomDatabase() { abstract fun dao(): FinanceDao }
