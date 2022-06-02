package ast

import Datatype

/**
 * synthetic parts of the ast implement this interface to indicate that they are synthetic
 */
interface SyntheticNode

/**
 * used to include synthetic classes/fields/functions in the ast. For example used for including things that are not
 * present in the source code (e.g. java.lang.Object). May also be useful in the future, when importing .jar files
 * is implemented
 */
object SyntheticAst {

    /**
     * represent java.lang.Object
     */
    val objectClass: SyntClass = SyntClass(
        "Object",
        staticFuncs = mutableListOf(),
        funcs = mutableListOf(),
        staticFields = mutableListOf(),
        fields = mutableListOf(),
        isAbstract = false,
        null,
        "java/lang/Object"
    )

    /**
     * represents the java.lang.String class. It is called $String in art to stop people from referring to it directly
     * and using str direct
     */
    val stringClass: SyntClass = SyntClass(
        "\$String",
        staticFuncs = mutableListOf(),
        funcs = mutableListOf(),
        staticFields = mutableListOf(),
        fields = mutableListOf(),
        isAbstract = false,
        objectClass,
        "java/lang/String"
    )


    init {
        objectClass.funcs.addAll(
            arrayOf(

                SyntFunction(
                    "toString",
                    FunctionDescriptor(mutableListOf(), Datatype.Str()),
                    isStatic = false,
                    isTopLevel = false,
                    isPrivate = false,
                    isAbstract = false,
                    objectClass
                ),

                SyntFunction(
                    "equals",
                    FunctionDescriptor(mutableListOf(Pair("other", Datatype.Object(objectClass))), Datatype.Bool()),
                    isStatic = false,
                    isTopLevel = false,
                    isPrivate = false,
                    isAbstract = false,
                    objectClass
                )

            )
        )

        objectClass.constructors.add(
            SyntConstructor(
                false,
                FunctionDescriptor(mutableListOf(), Datatype.Object(objectClass)),
                objectClass
            )
        )

        stringClass.funcs.add(
            SyntFunction(
                "startsWith",
                FunctionDescriptor(mutableListOf("prefix" to Datatype.Str()), Datatype.Bool()),
                isStatic = false,
                isTopLevel = false,
                isPrivate = false,
                isAbstract = false,
                clazz = stringClass
            )
        )
    }

    /**
     * represent a synthetic class
     * @param name the name used to refer to this class in the source code
     * @param staticFuncs the static functions present in this class
     * @param fields the non-static functions present in this class
     * @param staticFields the static fields present in this class
     * @param filds the non-static fields present in the class
     * @param extends the superclass of this class; null if this java.lang.Object
     * @param jvmName the name that is used to refer to this on the jvm
     */
    class SyntClass(
        override val name: String,
        staticFuncs: MutableList<Function>,
        funcs: MutableList<Function>,
        staticFields: MutableList<Field>,
        fields: MutableList<Field>,
        override val isAbstract: Boolean,
        override val extends: ArtClass?,
        override val jvmName: String
    ) : AstNode.ArtClass(staticFuncs, funcs, staticFields, fields, mutableListOf(), listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
    }

    /**
     * represents a synthetic function
     * @param name the name of this function
     * @param functionDescriptor the descriptor of this function
     * @param isStatic true if this function is static
     * @param isTopLevel true if this function is located in the top-level
     * @param isPrivate true if this function is private
     * @param clazz the class in which this function is defined; null if this function is defined in the top-level
     */
    class SyntFunction(
        override val name: String,
        override val functionDescriptor: FunctionDescriptor,
        override val isStatic: Boolean,
        override val isTopLevel: Boolean,
        override val isPrivate: Boolean,
        override val isAbstract: Boolean,
        override var clazz: ArtClass?
    ) : AstNode.Function(listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
    }

    /**
     * represents a synthetic field
     * @param name the name of this field
     * @param isPrivate true if the field is private
     * @param isConst true if the field is static
     * @param isTopLevel true if the field is defined in the top-level
     * @param fieldType the datatype that is stored in the field
     * @param isConst true if the field is constant (final)
     * @param clazz the class in which the field is defined; null if the field is defined in the top-level
     */
    class SyntField(
        override val name: String,
        override val isPrivate: Boolean,
        override val isStatic: Boolean,
        override val isTopLevel: Boolean,
        override val fieldType: Datatype,
        override val isConst: Boolean,
        override var clazz: ArtClass?
    ) : AstNode.Field(listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")

    }

    /**
     * represents a synthetic constructor
     * @param isPrivate true if the constructor is private
     * @param descriptor the descriptor containing the argument types of the constructor
     * @param clazz the class which this constructor instantiates
     */
    open class SyntConstructor(
        override val isPrivate: Boolean,
        override val descriptor: FunctionDescriptor,
        override var clazz: ArtClass
    ) : AstNode.Constructor(listOf()), SyntheticNode {

        override fun swap(orig: AstNode, to: AstNode) = throw CantSwapException()

        override fun <T> accept(visitor: AstNodeVisitor<T>): T = throw RuntimeException("cant visit synthetic node")
    }

    /**
     * represents the default constructor that is emitted if a class does not define a constructor
     */
    class DefaultConstructor(
        clazz: ArtClass
    ) : SyntConstructor(false, FunctionDescriptor(mutableListOf(), Datatype.Object(clazz)), clazz)

    /**
     * adds the synthetic tree to the real tree
     * @param root the real tree
     */
    fun addSyntheticTreeParts(root: AstNode.Program) {
        root.classes.add(objectClass)
        root.classes.add(stringClass)
    }

    @Deprecated("Only for testing, will probably remove soon")
    fun addSwingTreeParts(root: AstNode.Program) {

        val componentClass = SyntClass(
            "Component",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            objectClass,
            "java/awt/Component"
        )
        root.classes.add(componentClass)

        val containerClass = SyntClass(
            "Container",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            componentClass,
            "java/awt/Container"
        )
        root.classes.add(containerClass)

        val windowClass = SyntClass(
            "Window",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            containerClass,
            "java/awt/Window"
        )
        root.classes.add(windowClass)

        val windowSetSize = SyntFunction(
            "setSize",
            FunctionDescriptor(
                mutableListOf("width" to Datatype.Integer(), "height" to Datatype.Integer()),
                Datatype.Void()
            ),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = windowClass
        )
        windowClass.funcs.add(windowSetSize)

        val windowSetVisible = SyntFunction(
            "setVisible",
            FunctionDescriptor(mutableListOf("visible" to Datatype.Bool()), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = windowClass
        )
        windowClass.funcs.add(windowSetVisible)

        val frameClass = SyntClass(
            "Frame",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            windowClass,
            "java/awt/Frame"
        )
        root.classes.add(frameClass)

        val frameSetResizable = SyntFunction(
            "setResizable",
            FunctionDescriptor(mutableListOf("resizable" to Datatype.Bool()), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = frameClass
        )
        frameClass.funcs.add(frameSetResizable)

        val jFrameClass = SyntClass(
            "JFrame",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            frameClass,
            "javax/swing/JFrame"
        )
        root.classes.add(jFrameClass)

        jFrameClass.constructors.add(DefaultConstructor(jFrameClass))

        val jFrameSetClose = SyntFunction(
            "setDefaultCloseOperation",
            FunctionDescriptor(mutableListOf("operation" to Datatype.Integer()), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = jFrameClass
        )
        jFrameClass.funcs.add(jFrameSetClose)

        val jFrameExitField = SyntField(
            "EXIT_ON_CLOSE",
            isPrivate = false,
            isStatic = true,
            isTopLevel = false,
            fieldType = Datatype.Integer(),
            isConst = true,
            jFrameClass
        )
        jFrameClass.staticFields.add(jFrameExitField)

        val awtEventClass = SyntClass(
            "AWTEvent",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            objectClass,
            "java/awt/AWTEvent"
        )
        root.classes.add(awtEventClass)

        val componentEventClass = SyntClass(
            "ComponentEvent",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            awtEventClass,
            "java/awt/event/ComponentEvent"
        )
        root.classes.add(componentEventClass)

        val inputEventClass = SyntClass(
            "InputEvent",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            componentEventClass,
            "java/awt/event/InputEvent"
        )
        root.classes.add(inputEventClass)

        val mouseEventClass = SyntClass(
            "MouseEvent",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            inputEventClass,
            "java/awt/event/MouseEvent"
        )
        root.classes.add(mouseEventClass)

        val mouseEventParamString = SyntFunction(
            "paramString",
            FunctionDescriptor(mutableListOf(), Datatype.Str()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = mouseEventClass
        )
        mouseEventClass.funcs.add(mouseEventParamString)

        val layoutManagerClass = SyntClass(
            "LayoutManager",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            componentClass,
            "java/awt/LayoutManager"
        )
        root.classes.add(layoutManagerClass)

        val containerAdd = SyntFunction(
            "add",
            FunctionDescriptor(
                mutableListOf("component" to Datatype.Object(componentClass)),
                Datatype.Object(componentClass)
            ),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = containerClass
        )
        containerClass.funcs.add(containerAdd)

        val containerSetLayout = SyntFunction(
            "setLayout",
            FunctionDescriptor(mutableListOf("layout" to Datatype.Object(layoutManagerClass)), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = containerClass
        )
        containerClass.funcs.add(containerSetLayout)

        val containerProcessEvent = SyntFunction(
            "processEvent",
            FunctionDescriptor(mutableListOf("e" to Datatype.Object(awtEventClass)), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = containerClass
        )
        containerClass.funcs.add(containerProcessEvent)

        val jComponentClass = SyntClass(
            "JComponent",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            containerClass,
            "javax/swing/JComponent"
        )
        root.classes.add(jComponentClass)

        val jLabelClass = SyntClass(
            "JLabel",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            containerClass,
            "javax/swing/JLabel"
        )
        root.classes.add(jLabelClass)

        jLabelClass.constructors.add(DefaultConstructor(jLabelClass))

        val jLabelSetText = SyntFunction(
            "setText",
            FunctionDescriptor(mutableListOf("text" to Datatype.Str()), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = jLabelClass
        )
        jLabelClass.funcs.add(jLabelSetText)

        val boxClass = SyntClass(
            "Box",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            jComponentClass,
            "javax/swing/Box"
        )
        root.classes.add(boxClass)

        val createVerticalBox = SyntFunction(
            "createVerticalBox",
            FunctionDescriptor(mutableListOf(), Datatype.Object(boxClass)),
            isStatic = true,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = boxClass
        )
        boxClass.staticFuncs.add(createVerticalBox)

        val createVerticalStrut = SyntFunction(
            "createVerticalStrut",
            FunctionDescriptor(mutableListOf("height" to Datatype.Integer()), Datatype.Object(componentClass)),
            isStatic = true,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = boxClass
        )
        boxClass.staticFuncs.add(createVerticalStrut)

        val jPanelClass = SyntClass(
            "JPanel",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            jComponentClass,
            "javax/swing/JPanel"
        )
        root.classes.add(jPanelClass)

        jPanelClass.constructors.add(DefaultConstructor(jPanelClass))

        val gridLayoutClass = SyntClass(
            "GridLayout",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            layoutManagerClass,
            "java/awt/GridLayout"
        )
        root.classes.add(gridLayoutClass)

        val gridLayoutConstructor = SyntConstructor(
            false,
            FunctionDescriptor(mutableListOf("rows" to Datatype.Integer(), "cols" to Datatype.Integer()), Datatype.Object(gridLayoutClass)),
            gridLayoutClass
        )
        gridLayoutClass.constructors.add(gridLayoutConstructor)

        val abstractButtonClass = SyntClass(
            "AbstractButton",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            jComponentClass,
            "javax/swing/AbstractButton"
        )
        root.classes.add(abstractButtonClass)

        val jButtonClass = SyntClass(
            "JButton",
            staticFuncs = mutableListOf(),
            funcs = mutableListOf(),
            staticFields = mutableListOf(),
            fields = mutableListOf(),
            false,
            abstractButtonClass,
            "javax/swing/JButton"
        )
        root.classes.add(jButtonClass)

        val jButtonConstructor = SyntConstructor(
            false,
            FunctionDescriptor(mutableListOf("text" to Datatype.Str()), Datatype.Object(jButtonClass)),
            jButtonClass
        )
        jButtonClass.constructors.add(jButtonConstructor)

        val abstractButtonSetText = SyntFunction(
            "setText",
            FunctionDescriptor(mutableListOf("text" to Datatype.Str()), Datatype.Void()),
            isStatic = false,
            isTopLevel = false,
            isPrivate = false,
            isAbstract = false,
            clazz = abstractButtonClass
        )
        abstractButtonClass.funcs.add(abstractButtonSetText)

    }
}
