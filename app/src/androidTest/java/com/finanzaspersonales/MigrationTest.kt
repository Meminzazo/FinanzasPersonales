package com.finanzaspersonales
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finanzaspersonales.data.local.FinanceDatabase
import com.finanzaspersonales.data.repository.LocalFinanceRepository
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
@RunWith(AndroidJUnit4::class) class MigrationTest {
 @Test fun keepsVersionTwoPaymentsAndAddsEmptyInstallments()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val name="migration-v2-test.db";context.deleteDatabase(name)
  val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
  SQLiteDatabase.openOrCreateDatabase(file,null).use {old ->
   old.execSQL("""CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `movements` (`id` TEXT NOT NULL, `cents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `date` TEXT NOT NULL, `note` TEXT NOT NULL, `occurrence` TEXT, `accountId` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_movements_occurrence` ON `movements` (`occurrence`)""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `schedules` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `cents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `due` TEXT NOT NULL, `frequency` TEXT NOT NULL, `anchorDay` INTEGER NOT NULL, `accountId` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `target` INTEGER NOT NULL, `deadline` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `savings` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `cents` INTEGER NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `credit_accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `openingCents` INTEGER NOT NULL, `openedOn` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `debt_payments` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `cents` INTEGER NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("INSERT INTO categories VALUES ('i','Sueldo','INCOME',0)")
   old.execSQL("INSERT INTO movements VALUES ('m',3000,'i','2026-01-01','conservar',NULL,NULL)")
   old.execSQL("INSERT INTO credit_accounts VALUES ('a','Tarjeta','CARD',2000,'2026-01-01',0)")
   old.execSQL("INSERT INTO debt_payments VALUES ('pay','a',500,'2026-01-01')")
   old.version=2
  }
  val migrated=Room.databaseBuilder(context,FinanceDatabase::class.java,name).build()
  try {val l=LocalFinanceRepository(migrated).snapshot();assertEquals(2500L,l.balance());assertEquals(1500L,l.pending("a"));assertNull(l.payments.single().planId);assertTrue(l.plans.isEmpty())}
  finally {migrated.close();context.deleteDatabase(name)}
 }
 @Test fun keepsVersionThreePlanAndPaymentsWhenAddingDistribution()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val name="migration-v3-test.db";context.deleteDatabase(name)
  val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
  SQLiteDatabase.openOrCreateDatabase(file,null).use {old ->
   old.execSQL("""CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `movements` (`id` TEXT NOT NULL, `cents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `date` TEXT NOT NULL, `note` TEXT NOT NULL, `occurrence` TEXT, `accountId` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_movements_occurrence` ON `movements` (`occurrence`)""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `schedules` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `cents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `due` TEXT NOT NULL, `frequency` TEXT NOT NULL, `anchorDay` INTEGER NOT NULL, `accountId` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `target` INTEGER NOT NULL, `deadline` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `savings` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `cents` INTEGER NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `credit_accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `openingCents` INTEGER NOT NULL, `openedOn` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `debt_payments` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `cents` INTEGER NOT NULL, `date` TEXT NOT NULL, `planId` TEXT, `installment` INTEGER, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_debt_payments_planId_installment` ON `debt_payments` (`planId`, `installment`)""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `installment_plans` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `accountId` TEXT NOT NULL, `totalCents` INTEGER NOT NULL, `months` INTEGER NOT NULL, `paidBefore` INTEGER NOT NULL, `firstDue` TEXT NOT NULL, `purchaseDate` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `historical` INTEGER NOT NULL, `includedInDebt` INTEGER NOT NULL, `registeredOn` TEXT NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("INSERT INTO categories VALUES ('e','Compras','EXPENSE',0)")
   old.execSQL("INSERT INTO credit_accounts VALUES ('a','Tarjeta','CARD',0,'2026-01-01',0)")
   old.execSQL("INSERT INTO movements VALUES ('msi-p',12000,'e','2026-01-01','Laptop','MSI:p','a')")
   old.execSQL("INSERT INTO installment_plans VALUES ('p','Laptop','a',12000,3,0,'2026-01-31','2026-01-01','e',0,0,'2026-01-01')")
   old.execSQL("INSERT INTO debt_payments VALUES ('pay','a',4000,'2026-01-01','p',1)")
   old.version=3
  }
  val migrated=Room.databaseBuilder(context,FinanceDatabase::class.java,name).build()
  try {val l=LocalFinanceRepository(migrated).snapshot();com.finanzaspersonales.domain.model.Rules.validate(l);assertEquals(4000L,l.plans.single().amount(1));assertEquals(8000L,l.pending("a"));assertEquals(4000L,l.total(com.finanzaspersonales.domain.model.Kind.EXPENSE,java.time.YearMonth.of(2026,1)))}
  finally {migrated.close();context.deleteDatabase(name)}
 }
 @Test fun keepsVersionOneMovementsWhenOpeningLatestSchema()=runBlocking {
  val context=ApplicationProvider.getApplicationContext<Context>();val name="migration-test.db"
  context.deleteDatabase(name)
  val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
  SQLiteDatabase.openOrCreateDatabase(file,null).use {old ->
   old.execSQL("""CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `movements` (`id` TEXT NOT NULL, `cents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `date` TEXT NOT NULL, `note` TEXT NOT NULL, `occurrence` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_movements_occurrence` ON `movements` (`occurrence`)""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `schedules` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `cents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `due` TEXT NOT NULL, `frequency` TEXT NOT NULL, `anchorDay` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `target` INTEGER NOT NULL, `deadline` TEXT, PRIMARY KEY(`id`))""")
   old.execSQL("""CREATE TABLE IF NOT EXISTS `savings` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `cents` INTEGER NOT NULL, `date` TEXT NOT NULL, PRIMARY KEY(`id`))""")
   old.execSQL("INSERT INTO categories VALUES ('i','Sueldo','INCOME',0)")
   old.execSQL("INSERT INTO movements VALUES ('m',12345,'i','2026-01-01','conservar',NULL)")
   old.version=1
  }
  val migrated=Room.databaseBuilder(context,FinanceDatabase::class.java,name).build()
  try {
   val ledger=LocalFinanceRepository(migrated).snapshot()
   assertEquals(12345L,ledger.movements.single().cents);assertNull(ledger.movements.single().accountId);assertEquals("conservar",ledger.movements.single().note)
   assertTrue(ledger.accounts.isEmpty());assertTrue(ledger.payments.isEmpty())
  } finally {migrated.close();context.deleteDatabase(name)}
 }
}
