/*
 * Copyright 2011 Pierre-Yves Ricau (py.ricau at gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package info.piwai.buildergen.modeling;

import static com.sun.codemodel.JExpr._this;
import info.piwai.buildergen.api.Buildable;
import info.piwai.buildergen.api.Builder;
import info.piwai.buildergen.api.Mandatory;
import info.piwai.buildergen.api.UncheckedBuilder;
import info.piwai.buildergen.helper.ElementHelper;
import info.piwai.buildergen.processing.BuilderGenProcessor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.Generated;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

/**
 * Builds the {@link JCodeModel} content based on the given annotated elements.
 * Those elements must be valid.
 * 
 * @author Pierre-Yves Ricau (py.ricau at gmail.com)
 */
public class ModelBuilder {

	private final ElementHelper elementHelper;
	private final JCodeModel codeModel;

	public ModelBuilder(JCodeModel codeModel, ElementHelper elementHelper) {
		this.elementHelper = elementHelper;
		this.codeModel = codeModel;
	}

	/**
	 * 
	 * @param buildableElement
	 *            The {@link Buildable} annotated element to build the model
	 *            for. This element must be valid.
	 * @throws JClassAlreadyExistsException
	 *             if a builder has already been created for this element
	 */
	public void buildClass(TypeElement buildableElement) throws JClassAlreadyExistsException {

		String builderFullyQualifiedName = extractBuilderFullyQualifiedName(buildableElement);

		Set<ExecutableElement> constructors = elementHelper.findAccessibleConstructors(buildableElement);

		ExecutableElement constructor = elementHelper.findBuilderConstructor(constructors);

		JDefinedClass builderClass = codeModel._class(builderFullyQualifiedName);

		JClass buildableClass = codeModel.ref(buildableElement.getQualifiedName().toString());

		SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

		builderClass.annotate(Generated.class) //
				.param("comments", "Generated by BuilderGen") //
				.param("value", BuilderGenProcessor.class.getName()) //
				.param("date", isoDateFormat.format(new Date())) //
		;

		List<? extends VariableElement> parameters = constructor.getParameters();
		for (VariableElement parameter : parameters) {
			String paramName = parameter.getSimpleName().toString();
			String paramClassFullyQualifiedName = parameter.asType().toString();
			JClass paramClass = codeModel.ref(paramClassFullyQualifiedName);
			JFieldVar setterField = builderClass.field(JMod.PRIVATE, paramClass, paramName);

			JMethod setter = builderClass.method(JMod.PUBLIC, builderClass, paramName);
			JVar setterParam = setter.param(paramClass, paramName);
			setter.body() //
					.assign(_this().ref(setterField), setterParam) //
					._return(_this());

			JDocComment javadoc = setter.javadoc() //
					.append("Setter for the ") //
					.append(paramName) //
					.append(" parameter.");

			javadoc.addParam(setterParam) //
					.append("the value for the ") //
					.append(paramName) //
					.append(" constructor parameter of the ") //
					.append(buildableClass) //
					.append(" class.");

			javadoc.addReturn() //
					.append("this, ie the ") //
					.append(builderClass) //
					.append(" instance, to enable chained calls.");
		}

		List<VariableElement> mandatoryParameters = new ArrayList<VariableElement>();
		for (VariableElement parameter : parameters) {
			if (parameter.getAnnotation(Mandatory.class) != null) {
				mandatoryParameters.add(parameter);
			}
		}

		JMethod buildMethod = builderClass.method(JMod.PUBLIC, buildableClass, "build");

		JDocComment javadoc = buildMethod.javadoc() //
				.append("Creates ") //
				.append(buildableClass) //
				.append(" instances based on this builder fields.<br />\n") //
				.append("<br />\n") //
				.append("The builder keeps its state after this method has been called.");

		javadoc.addReturn() //
				.append("a new ") //
				.append(buildableClass) //
				.append(" instance.") //
		;

		boolean hasCheckedExceptions = false;
		JClass runtimeException = codeModel.ref(RuntimeException.class);
		List<? extends TypeMirror> thrownTypes = constructor.getThrownTypes();
		for (TypeMirror thrownType : thrownTypes) {
			JClass thrownClass = codeModel.ref(thrownType.toString());
			buildMethod._throws(thrownClass);

			javadoc.addThrows(thrownClass) //
					.append(" when ") //
					.append(buildableClass)//
					.append("'s constructor throws this exception") //
			;
			if (!runtimeException.isAssignableFrom(thrownClass)) {
				hasCheckedExceptions = true;
			}
		}

		if (hasCheckedExceptions) {
			JClass builderInterface = codeModel.ref(Builder.class);
			JClass narrowedInterface = builderInterface.narrow(buildableClass);
			builderClass._implements(narrowedInterface);
		} else {
			JClass builderInterface = codeModel.ref(UncheckedBuilder.class);
			JClass narrowedInterface = builderInterface.narrow(buildableClass);
			builderClass._implements(narrowedInterface);
		}

		JBlock buildBody = buildMethod.body();
		JInvocation newBuildable = JExpr._new(buildableClass);

		for (VariableElement parameter : constructor.getParameters()) {
			String paramName = parameter.getSimpleName().toString();
			newBuildable.arg(JExpr.ref(paramName));
		}

		buildBody._return(newBuildable);

		JClass mandatoryClass = codeModel.ref(Mandatory.class);
		if (!mandatoryParameters.isEmpty()) {
			JMethod builderConstructor = builderClass.constructor(JMod.PUBLIC);
			JBlock constructorBody = builderConstructor.body();

			JDocComment constructorJavadoc = builderConstructor.javadoc() //
					.append("Constructor with @") //
					.append(mandatoryClass) //
					.append(" parameters.") //

			;

			for (VariableElement parameter : mandatoryParameters) {
				String paramName = parameter.getSimpleName().toString();
				JFieldVar paramField = builderClass.fields().get(paramName);
				JType paramClass = paramField.type();
				JVar constructorParam = builderConstructor.param(paramClass, paramName);

				constructorBody.assign(_this().ref(paramField), constructorParam);

				constructorJavadoc.addParam(constructorParam) //
						.append("the value for the ") //
						.append(paramName) //
						.append(" @").append(mandatoryClass) //
						.append(" constructor parameter of the ") //
						.append(buildableClass) //
						.append(" class.") //
				;
			}
		}

		String factoryMethodName = extractFactoryMethodName(buildableElement);
		JMethod factoryMethod = builderClass.method(JMod.PUBLIC | JMod.STATIC, builderClass, factoryMethodName);
		JBlock factoryMethodBody = factoryMethod.body();

		JDocComment factoryMethodJavadoc = factoryMethod.javadoc();

		JInvocation newBuilder = JExpr._new(builderClass);
		if (!mandatoryParameters.isEmpty()) {
			for (VariableElement parameter : mandatoryParameters) {
				String paramName = parameter.getSimpleName().toString();
				JFieldVar paramField = builderClass.fields().get(paramName);
				JType paramClass = paramField.type();

				JVar createParam = factoryMethod.param(paramClass, paramName);

				newBuilder.arg(createParam);

				factoryMethodJavadoc.addParam(createParam) //
						.append("the value for the ") //
						.append(paramName) //
						.append(" @").append(mandatoryClass) //
						.append(" constructor parameter of the ") //
						.append(buildableClass) //
						.append(" class.") //
				;
			}
		}
		factoryMethodBody._return(newBuilder);

		factoryMethodJavadoc.append("Static factory method for ") //
				.append(builderClass) //
				.append(" instances.") //
				.addReturn() //
				.append("a new ") //
				.append(builderClass) //
				.append("instance") //
		;

		addBuilderClassJavadoc(builderClass, buildableClass);
	}

	private void addBuilderClassJavadoc(JDefinedClass builderClass, JClass buildableClass) {
		builderClass //
				.javadoc() //
				.append("Builder for the ") //
				.append(buildableClass) //
				.append(" class.<br />\n") //
				.append("<br />\n") //
				.append("This builder implements Joshua Bloch's builder pattern, to let you create ") //
				.append(buildableClass) //
				.append(" instances \nwithout having to deal with complex constructor parameters.\n") //
				.append("It has a fluid interface, which mean you can chain calls to its methods.<br />\n") //
				.append("<br />\n") //
				.append("You can create a new ") //
				.append(builderClass) //
				.append(" instance by calling the ") //
				.append("{@link #create()}") //
				.append(" static method, \nor its constructor directly.<br />\n") //
				.append("<br />\n") //
				.append("When done with settings fields, you can create ") //
				.append(buildableClass) //
				.append(" instances \nby calling the ") //
				.append("{@link #build()}") //
				.append(" method.\n") //
				.append("Each call will return a new instance.<br />\n") //
				.append("<br />\n") //
				.append("You can call setters multiple times, and use this builder as an object template.") //
		;
	}

	private String extractBuilderFullyQualifiedName(TypeElement buildableElement) {
		Buildable buildableAnnotation = buildableElement.getAnnotation(Buildable.class);
		String builderSuffix = buildableAnnotation.value();

		String buildableFullyQualifiedName = buildableElement.getQualifiedName().toString();

		return buildableFullyQualifiedName + builderSuffix;
	}

	private String extractFactoryMethodName(TypeElement buildableElement) {
		Buildable buildableAnnotation = buildableElement.getAnnotation(Buildable.class);
		return buildableAnnotation.factoryMethod();
	}
}
