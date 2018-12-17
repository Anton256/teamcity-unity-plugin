package jetbrains.buildServer.unity.fetchers

import org.jetbrains.unity.CSharpParser
import org.jetbrains.unity.CSharpParserBaseListener
import java.util.*

class UnityStaticMethodNamesListener : CSharpParserBaseListener() {

    val names = linkedMapOf<String, String?>()

    override fun enterMethod_declaration(method: CSharpParser.Method_declarationContext) {
        method.formal_parameter_list()?.let {
            if (!it.isEmpty) return
        }

        val commonMember = method.parent as CSharpParser.Common_member_declarationContext
        if (commonMember.children?.firstOrNull()?.text != "void") return

        val classMember = commonMember.parent as CSharpParser.Class_member_declarationContext
        val modifiers = classMember.all_member_modifiers().all_member_modifier().flatMap { context ->
            context.children.map { it.text }
        }

        if (!modifiers.any { METHOD_REQUIRED.contains(it) } || modifiers.any { METHOD_EXCLUDE.contains(it) }) {
            return
        }

        names += getMethodReference(classMember, method) to getDescription(classMember)
    }

    private fun getMethodReference(classMember: CSharpParser.Class_member_declarationContext,
                                   method: CSharpParser.Method_declarationContext): String {
        val classDefinition = classMember.parent.parent.parent as CSharpParser.Class_definitionContext
        return "${classDefinition.identifier().text}.${method.method_member_name().text}"
    }

    private fun getDescription(classMember: CSharpParser.Class_member_declarationContext): String? {
        classMember.attributes()?.children
                ?.filterIsInstance<CSharpParser.Attribute_sectionContext>()
                ?.forEach {
                    val attribute = it.attribute_list().children
                            .filterIsInstance<CSharpParser.AttributeContext>()
                            .firstOrNull { attribute ->
                                attribute.namespace_or_type_name().text == "MenuItem"
                            } ?: return null
                    return attribute.attribute_argument().first().text.trim('"')
                } ?: return null
        return null
    }

    companion object {
        val METHOD_REQUIRED = TreeSet<String>(String.CASE_INSENSITIVE_ORDER).apply {
            add("static")
        }
        val METHOD_EXCLUDE = TreeSet<String>(String.CASE_INSENSITIVE_ORDER).apply {
            add("private")
            add("internal")
        }
    }
}