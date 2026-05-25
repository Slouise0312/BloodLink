package com.example.bloodlink

/**
 * Multi-step questionnaire answers: question index -> value (boolean or choice).
 * Rule engine evaluates to QuestionnaireOutcome (ELIGIBLE / TEMP_DEFERRED / NOT_ELIGIBLE + reason + deferralUntil).
 */
object QuestionnaireRuleEngine {

    val QUESTIONS: List<String> = listOf(
        "In the last 14 days: fever, flu, cough, colds, or feeling unwell?",            // Q0
        "Pregnant, gave birth in the last 6 weeks, or currently breastfeeding?",        // Q1
        "In the last 12 months: tattoo, body piercing, or acupuncture?",                // Q2
        "In the last 12 months: surgery, tooth extraction, or significant blood loss?", // Q3
        "Currently taking antibiotics or any medication affecting blood safety?",        // Q4
        "In the last 12 hours: consumed alcohol, or less than 5 hours of sleep?",       // Q5
        "Do you have uncontrolled high blood pressure or a heart condition?",            // Q6
        "Have you ever had hepatitis B, hepatitis C, HIV, or any blood-borne disease?", // Q7
        "In the last 12 months: received a blood transfusion or organ transplant?",     // Q8
        "Any other reason you believe you may not be safe or eligible to donate?"       // Q9
    )

    // PNRC/DOH-aligned deferral period helpers (in milliseconds)
    private fun days(d: Long) = d * 24L * 60 * 60 * 1000
    private fun months(m: Long) = days(m * 30L)
    private fun weeks(w: Long) = days(w * 7L)

    fun evaluate(answers: Map<Int, Boolean>): QuestionnaireOutcome {
        val now = System.currentTimeMillis()

        // Q0 — Illness/fever: 14 days after full recovery (PNRC standard)
        if (answers[0] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Recent illness or fever — defer until 14 days after full recovery",
            now + days(14)
        )

        // Q1 — Pregnancy/postpartum: 6 weeks after delivery; 6 months if breastfeeding (DOH)
        if (answers[1] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Pregnancy or postpartum — defer at least 6 weeks after delivery (6 months if breastfeeding)",
            now + weeks(6)
        )

        // Q2 — Tattoo/piercing/acupuncture: 12 months (PNRC — hepatitis B/C window period)
        if (answers[2] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Recent tattoo, piercing, or acupuncture — defer 12 months",
            now + months(12)
        )

        // Q3 — Surgery/tooth extraction: 12 months for surgery; 3 days for simple dental (PNRC)
        if (answers[3] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Recent surgery or tooth extraction — defer 12 months (surgery) or 3 days (simple dental)",
            now + months(12)
        )

        // Q4 — Medications: case-dependent; refer to licensed screener (DOH advisory)
        if (answers[4] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Currently on medication — please refer to a licensed blood bank screener for assessment",
            now + days(7)
        )

        // Q5 — Alcohol/sleep: 12 hours after last drink; must have at least 5 hours sleep (PNRC)
        if (answers[5] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Alcohol intake or insufficient sleep — defer at least 12 hours",
            now + days(1)
        )

        // Q6 — Uncontrolled hypertension/heart condition: permanent deferral (PNRC)
        if (answers[6] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.NOT_ELIGIBLE,
            "Uncontrolled hypertension or heart condition — permanently deferred per PNRC guidelines",
            null
        )

        // Q7 — Hepatitis B/C, HIV, blood-borne disease: permanent deferral (PNRC/DOH)
        if (answers[7] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.NOT_ELIGIBLE,
            "History of hepatitis B, hepatitis C, HIV, or blood-borne infection — permanently deferred",
            null
        )

        // Q8 — Blood transfusion/organ transplant: 12 months (PNRC)
        if (answers[8] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.TEMP_DEFERRED,
            "Recent blood transfusion or organ transplant — defer 12 months",
            now + months(12)
        )

        // Q9 — Self-reported ineligibility: refer to screener
        if (answers[9] == true) return QuestionnaireOutcome(
            QuestionnaireStatus.NOT_ELIGIBLE,
            "Donor self-reported a possible disqualifying condition — refer to licensed screener",
            null
        )

        return QuestionnaireOutcome(QuestionnaireStatus.ELIGIBLE, null, null)
    }
}