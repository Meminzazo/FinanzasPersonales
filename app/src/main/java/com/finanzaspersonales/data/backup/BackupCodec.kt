package com.finanzaspersonales.data.backup
import com.finanzaspersonales.domain.model.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCodec {
 private val magic=byteArrayOf(70,80,66,49)
 private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
  require(password.size>=8) { "Usa una contraseña de al menos 8 caracteres." }
  val spec=PBEKeySpec(password,salt,210000,256)
  return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() }
 }
 fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
  val random=SecureRandom();val salt=ByteArray(16).also(random::nextBytes);val iv=ByteArray(12).also(random::nextBytes)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv));cipher.updateAAD(magic)
  return magic+salt+iv+cipher.doFinal(plain)
 }
 fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
  require(bytes.size in 48..20_000_000 && bytes.take(4).toByteArray().contentEquals(magic)) { "Este archivo no es un respaldo compatible." }
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,bytes.copyOfRange(4,20)),GCMParameterSpec(128,bytes.copyOfRange(20,32)));cipher.updateAAD(magic)
  return cipher.doFinal(bytes.copyOfRange(32,bytes.size))
 }
 fun encode(l: Ledger): ByteArray {
  fun obj(vararg pairs: Pair<String,Any?>) = JSONObject().apply { pairs.forEach { put(it.first,it.second ?: JSONObject.NULL) } }
  fun <T> arr(list: List<T>, f:(T)->JSONObject)=JSONArray().apply { list.forEach { put(f(it)) } }
  return obj("version" to 6,
   "categories" to arr(l.categories) { obj("id" to it.id,"name" to it.name,"kind" to it.kind.name,"archived" to it.archived) },
   "movements" to arr(l.movements) { obj("id" to it.id,"cents" to it.cents,"category" to it.categoryId,"date" to it.date.toString(),"note" to it.note,"occurrence" to it.occurrence,"account" to it.accountId) },
   "schedules" to arr(l.schedules) { obj("id" to it.id,"title" to it.title,"cents" to it.cents,"category" to it.categoryId,"due" to it.due.toString(),"frequency" to it.frequency.name,"anchor" to it.anchorDay,"account" to it.accountId,"paymentsTotal" to it.paymentsTotal,"paymentsRemaining" to it.paymentsRemaining) },
   "goals" to arr(l.goals) { obj("id" to it.id,"name" to it.name,"target" to it.target,"deadline" to it.deadline?.toString()) },
   "accounts" to arr(l.accounts) { obj("id" to it.id,"name" to it.name,"type" to it.type.name,"opening" to it.openingCents,"openedOn" to it.openedOn.toString(),"archived" to it.archived,"cutoffDay" to it.cutoffDay) },
   "payments" to arr(l.payments) { obj("id" to it.id,"account" to it.accountId,"cents" to it.cents,"date" to it.date.toString(),"plan" to it.planId,"installment" to it.installment) },
   "plans" to arr(l.plans) { obj("id" to it.id,"title" to it.title,"account" to it.accountId,"total" to it.totalCents,"months" to it.months,"paidBefore" to it.paidBefore,"firstDue" to it.firstDue.toString(),"purchaseDate" to it.purchaseDate.toString(),"category" to it.categoryId,"historical" to it.historical,"includedInDebt" to it.includedInDebt,"registeredOn" to it.registeredOn.toString(),"amounts" to JSONArray(it.amounts)) },
   "savings" to arr(l.savings) { obj("id" to it.id,"goal" to it.goalId,"cents" to it.cents,"date" to it.date.toString()) }).toString().toByteArray(Charsets.UTF_8)
 }
 fun decode(bytes: ByteArray): Ledger {
  val root=JSONObject(bytes.toString(Charsets.UTF_8));val version=root.getInt("version");require(version in 1..6) { "Versión de respaldo no compatible." }
  fun <T> list(name:String,f:(JSONObject)->T):List<T> { val a=root.getJSONArray(name);return (0 until a.length()).map { f(a.getJSONObject(it)) } }
  return Ledger(
   list("categories") { Category(it.getString("id"),it.getString("name"),Kind.valueOf(it.getString("kind")),it.getBoolean("archived")) },
   list("movements") { Movement(it.getString("id"),it.getLong("cents"),it.getString("category"),LocalDate.parse(it.getString("date")),it.getString("note"),if(it.isNull("occurrence")) null else it.getString("occurrence"),if(it.isNull("account")) null else it.getString("account")) },
   list("schedules") { Schedule(it.getString("id"),it.getString("title"),it.getLong("cents"),it.getString("category"),LocalDate.parse(it.getString("due")),Frequency.valueOf(it.getString("frequency")),it.getInt("anchor"),if(it.isNull("account")) null else it.getString("account"),if(it.isNull("paymentsTotal")) null else it.getInt("paymentsTotal"),if(it.isNull("paymentsRemaining")) null else it.getInt("paymentsRemaining")) },
   list("goals") { Goal(it.getString("id"),it.getString("name"),it.getLong("target"),if(it.isNull("deadline")) null else LocalDate.parse(it.getString("deadline"))) },
   list("savings") { Saving(it.getString("id"),it.getString("goal"),it.getLong("cents"),LocalDate.parse(it.getString("date"))) },
   if(version==1) emptyList() else list("accounts") { CreditAccount(it.getString("id"),it.getString("name"),CreditType.valueOf(it.getString("type")),it.getLong("opening"),LocalDate.parse(it.getString("openedOn")),it.getBoolean("archived"),if(it.isNull("cutoffDay")) null else it.getInt("cutoffDay")) },
   if(version==1) emptyList() else list("payments") { DebtPayment(it.getString("id"),it.getString("account"),it.getLong("cents"),LocalDate.parse(it.getString("date")),if(it.isNull("plan")) null else it.getString("plan"),if(it.isNull("installment")) null else it.getInt("installment")) },
   if(version<3) emptyList() else list("plans") { InstallmentPlan(it.getString("id"),it.getString("title"),it.getString("account"),it.getLong("total"),it.getInt("months"),it.getInt("paidBefore"),LocalDate.parse(it.getString("firstDue")),LocalDate.parse(it.getString("purchaseDate")),it.getString("category"),it.getBoolean("historical"),it.getBoolean("includedInDebt"),LocalDate.parse(it.getString("registeredOn")),if(version<4) emptyList() else it.getJSONArray("amounts").let {a->(0 until a.length()).map {i->a.getLong(i)}}) }).also(Rules::validate)
 }
}
