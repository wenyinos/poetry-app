# 数据模型由 org.json 手工解析，未使用反射，保留规则从简。
# 保留行号便于崩溃栈定位（体积代价很小）。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 桌面小组件由系统按类名实例化，不能被混淆或移除
-keep class me.javayhu.poetry.widget.** { *; }
