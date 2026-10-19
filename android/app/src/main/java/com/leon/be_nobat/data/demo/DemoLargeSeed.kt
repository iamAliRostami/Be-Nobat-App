package com.leon.be_nobat.data.demo

import com.leon.be_nobat.domain.model.*
import java.util.Calendar
import java.util.TimeZone

/** Fixed identities with dates relative to the first launch, then persisted unchanged. */
internal fun DemoDatabaseState.expanded(): DemoDatabaseState {
    val firstNames = listOf("مریم", "علی", "سارا", "رضا", "نرگس", "امیر", "پریسا", "آرمان", "الهام", "پویا")
    val lastNames = listOf("احمدی", "رضایی", "کریمی", "محمدی", "حسینی", "رحیمی", "قاسمی", "موسوی", "جعفری", "صادقی")
    val customers = users.filter { "Customer" in it.roles } + (0 until 300).map { i ->
        ManagementUser(id = "demo_customer_$i", displayName = "${firstNames[i % 10]} ${lastNames[(i / 10) % 10]}", email = "customer$i@example.test", phoneNumber = "09" + (100000000L + i).toString(), active = true, roles = listOf("Customer"))
    }
    val newBusinesses = businesses.toMutableList()
    val newBranches = branches.toMutableList()
    val newServices = services.toMutableList()
    val newResources = resources.toMutableList()
    val links = branchServices.toMutableList()
    val qualifications = resourceServices.toMutableMap()
    val cities = listOf("تهران", "شیراز", "اصفهان", "مشهد", "تبریز", "رشت", "کرج", "یزد")
    for (i in 1..9) for (b in businesses) {
        val suffix = "_demo_$i"
        val city = cities[(i - 1) % cities.size]
        newBusinesses += b.copy(id = b.id + suffix, name = "${b.name} — $city ${i + 1}", slug = b.slug + suffix, city = city)
        for (branch in branches.filter { it.businessId == b.id }) {
            newBranches += branch.copy(id = branch.id + suffix, businessId = b.id + suffix, name = "${branch.name} $city", address = "$city، خیابان بهار، پلاک ${10 + i}")
            for (r in resources.filter { it.branchId == branch.id }) {
                newResources += r.copy(id = r.id + suffix, branchId = branch.id + suffix, userId = null)
                qualifications[r.id + suffix] = resourceServices[r.id].orEmpty().map { it + suffix }
            }
        }
        for (service in services.filter { it.businessId == b.id }) {
            newServices += service.copy(id = service.id + suffix, businessId = b.id + suffix, branchIds = service.branchIds.map { it + suffix }, resourceIds = service.resourceIds.map { it + suffix })
        }
        links += branchServices.filter { link -> services.any { it.id == link.serviceId && it.businessId == b.id } }.map { it.copy(id = it.id + suffix, branchId = it.branchId + suffix, serviceId = it.serviceId + suffix) }
    }
    val generated = mutableListOf<ManagementAppointment>()
    for ((index, r) in newResources.withIndex()) {
        val branch = newBranches.first { it.id == r.branchId }
        val business = newBusinesses.first { it.id == branch.businessId }
        val service = newServices.firstOrNull { it.id in qualifications[r.id].orEmpty() && branch.id in it.branchIds } ?: continue
        for (day in -30..14) {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
                add(Calendar.DAY_OF_YEAR, day); set(Calendar.HOUR_OF_DAY, branch.openHour + 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = demoFormat(calendar.timeInMillis)
            val end = demoFormat(calendar.timeInMillis + service.durationMinutes * 60000L)
            val customer = customers[Math.floorMod(index * 13 + day, customers.size)]
            val status = if (day < 0) listOf("Completed", "Completed", "Completed", "Cancelled", "NoShow")[Math.floorMod(day + index, 5)] else if (business.requiresApproval) "Pending" else "Confirmed"
            generated += ManagementAppointment(id = "seed_${index}_$day", businessId = business.id, businessName = business.name, branchId = branch.id, branchName = branch.name, serviceId = service.id, serviceName = service.name, serviceNames = listOf(service.name), resourceId = r.id, resourceName = r.name, customerId = customer.id, customerName = customer.displayName, customerEmail = customer.email, customerPhoneNumber = customer.phoneNumber, startsAt = start, endsAt = end, status = status, finalPrice = service.price, trackingCode = "BN-${100000 + generated.size}", customerNote = if (day % 3 == 0) "لطفاً نوبت در ساعت مقرر انجام شود." else "")
        }
    }
    val comments = listOf("برخورد محترمانه و خدمات دقیق بود.", "از کیفیت خدمات راضی هستم، دوباره مراجعه می‌کنم.", "محیط تمیز و نوبت‌دهی منظم بود.", "کیفیت خوب بود، کمی منتظر ماندم.")
    val generatedReviews = generated.filter { it.status == "Completed" }.filterIndexed { i, _ -> i % 3 == 0 }.mapIndexed { i, a ->
        ManagementReview(id = "seed_review_$i", businessId = a.businessId, branchId = a.branchId, customerId = a.customerId, customerName = a.customerName, appointmentId = a.id, rating = 3 + i % 3, comment = comments[i % comments.size], status = if (i % 7 == 0) "Pending" else "Approved", active = true, createdAt = a.endsAt)
    }

    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val genAvailabilities = mutableListOf<ManagementAvailability>()
    for (b in newBusinesses.take(15)) {
        val branchList = newBranches.filter { it.businessId == b.id }
        for (branch in branchList) {
            for (day in 0..14) {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
                    add(Calendar.DAY_OF_YEAR, day)
                }
                val dateStr = demoFormat(calendar.timeInMillis).take(10)
                val dayOfWeek = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
                genAvailabilities += ManagementAvailability(
                    id = "avail_${branch.id}_$day",
                    businessId = b.id,
                    branchId = branch.id,
                    effectiveDate = dateStr,
                    dayOfWeek = dayOfWeek,
                    startsAt = String.format(java.util.Locale.US, "%02d:00:00", branch.openHour),
                    endsAt = String.format(java.util.Locale.US, "%02d:00:00", branch.closeHour),
                    isAvailable = true,
                )
            }
        }
    }

    val customerReviewComments = listOf(
        "مشتری بسیار وقت‌شناس و محترم.",
        "نوبت به‌موقع انجام شد و هماهنگی عالی بود.",
        "رفتار بسیار خوب و بدون هیچ تأخیری.",
        "مشتری همیشگی و وفادار مجموعه."
    )
    val genCustomerReviews = generated.filter { it.status == "Completed" }.take(30).mapIndexed { i, a ->
        ManagementCustomerReview(
            id = "cust_rev_$i",
            appointmentId = a.id,
            businessId = a.businessId,
            branchId = a.branchId,
            customerId = a.customerId,
            customerName = a.customerName,
            authorId = "user_manager",
            authorName = "آرش معتمدی",
            rating = 4 + (i % 2),
            comment = customerReviewComments[i % customerReviewComments.size],
            createdAt = a.endsAt,
        )
    }

    return copy(
        users = users.filterNot { "Customer" in it.roles } + customers,
        businesses = newBusinesses,
        branches = newBranches,
        services = newServices,
        branchServices = links,
        resources = newResources,
        resourceServices = qualifications,
        appointments = generated.sortedByDescending { it.startsAt },
        reviews = generatedReviews,
        customerReviews = this.customerReviews + genCustomerReviews,
        availabilities = this.availabilities + genAvailabilities,
    )
}
