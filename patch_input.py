import sys

def patch_file():
    file_path = '/Users/xxxxholic/nozokima/app/src/main/java/com/example/nozokima/MainActivity.kt'
    with open(file_path, 'r', encoding='utf-8') as f:
        text = f.read()

    # 1. AI analysis setup
    target_ai = '''                showKeypad = false
                snackbarHostState.showSnackbar(if (isExpense) "支出を記録しました" else "収入を記録しました")'''
    replacement_ai = '''                showKeypad = false
                if (gemma != null && isExpense) {
                    val prompt = "私は今、$amountValue 円を「${transaction.category}」に使いました（メモ：${transaction.name}）。これに対する1行の短い節約アドバイスをください。"
                    val aiResponse = gemma.generateResponse(prompt)
                    snackbarHostState.showSnackbar(aiResponse.ifBlank { "記録しました！" })
                } else {
                    snackbarHostState.showSnackbar(if (isExpense) "支出を記録しました" else "収入を記録しました")
                }'''
    text = text.replace(target_ai, replacement_ai)

    # 2. Layout modifications
    start_str = 'Text("総額", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)'
    end_str = 'Spacer(modifier = Modifier.imePadding())'
    start_idx = text.find(start_str)
    end_idx = text.find(end_str)

    new_layout = '''
            if (isExpense && monthlyBudget > 0L) {
                Text("今月の消費状況", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, NotionBorder, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("使用済み: ¥${String.format(java.util.Locale.JAPAN, "%,d", spentThisMonth)}", fontSize = 14.sp, color = NotionTextPrimary)
                        Text("予算: ¥${String.format(java.util.Locale.JAPAN, "%,d", monthlyBudget)}", fontSize = 14.sp, color = NotionTextSecondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { goalRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = NotionSafeGreen,
                        trackColor = NotionBorder
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Text("記録内容", color = NotionTextSecondary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Column(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, NotionBorder, RoundedCornerShape(16.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showKeypad = !showKeypad }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.AttachMoney, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("金額", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(120.dp), color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = if (amountText.isEmpty()) "¥ 0" else "¥ $amountText",
                            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.Category, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("カテゴリ", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))

                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(modifier = Modifier.width(120.dp).clickable { expanded = true }, color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                            Text(
                                text = selectedCategory?.name ?: "選択...",
                                color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    leadingIcon = { Icon(cat.icon, null, modifier = Modifier.size(20.dp)) },
                                    onClick = { selectedCategory = cat; expanded = false }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showAssetSheet = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("資産 / 口座", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(120.dp), color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = selectedAssetEntity?.name ?: "選択...",
                            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.DateRange, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("日付", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(modifier = Modifier.width(120.dp), color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            text = dateFormatter.format(java.util.Date(selectedDate)),
                            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, NotionBorder)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(36.dp).background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), Alignment.Center) {
                        Icon(Icons.Default.EditNote, null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("メモ", color = NotionTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(40.dp))
                    Surface(modifier = Modifier.weight(1f), color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                            singleLine = true,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp).fillMaxWidth(),
                            decorationBox = { inner ->
                                if (memoText.isEmpty()) Text("任意入力", color = accentColor.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                inner()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 保存ボタン
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = NotionBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isSaveEnabled
                ) {
                    Text(
                        if (isExpense) "支出を記録する" else "収入を記録する",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.imePadding())
'''

    if start_idx != -1 and end_idx != -1:
        text = text[:start_idx] + new_layout + text[end_idx+len(end_str):]

    # add missing variables
    target3 = 'val dbAssets by dao.getAllAssets().collectAsState(initial = emptyList())'
    replacement3 = '''val dbAssets by dao.getAllAssets().collectAsState(initial = emptyList())
    val transactions by dao.getAllTransactions().collectAsState(initial = emptyList())
    val budgets by dao.getAllBudgets().collectAsState(initial = emptyList())
    val goalSetting by dao.getGoalSetting().collectAsState(initial = null)

    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val spentThisMonth = transactions.filter { it.date >= calendar.timeInMillis && it.isExpense }.sumOf { it.amount }

    val currentAssetsAmount = dbAssets.sumOf { it.amount }
    val currentGoal = goalSetting
    val goalMonthlyBudget = remember(currentGoal, currentAssetsAmount) {
        if (currentGoal != null && currentGoal.showResults && currentGoal.targetAmount > 0) {
            val remainingDays = ((currentGoal.targetDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
            val remainingMonths = (remainingDays / 30.0).coerceAtLeast(0.1)
            val totalExpectedIncome = (currentGoal.monthlyIncome * remainingMonths).toLong()
            val totalSpendable = (currentAssetsAmount + totalExpectedIncome - currentGoal.targetAmount).coerceAtLeast(0L)
            if (remainingMonths > 0) (totalSpendable / remainingMonths).toLong() else 0L
        } else {
            null
        }
    }
    val defaultBudget = budgets.sumOf { it.monthlyAmount }.let { if (it == 0) 100000L else it.toLong() }
    val monthlyBudget = goalMonthlyBudget ?: defaultBudget
    val goalRatio = if (monthlyBudget > 0L) (spentThisMonth.toFloat() / monthlyBudget.toFloat()).coerceIn(0f, 1f) else 0f
    '''

    # We should only replace the first occurrence (in InputScreen). We'll assume the string is unique enough anyway, or we find the right one.
    if target3 in text:
        text = text.replace(target3, replacement3, 1)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(text)

if __name__ == '__main__':
    patch_file()

