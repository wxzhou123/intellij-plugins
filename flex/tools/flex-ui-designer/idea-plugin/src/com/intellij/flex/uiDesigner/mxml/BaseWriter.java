package com.intellij.flex.uiDesigner.mxml;

import com.intellij.flex.uiDesigner.AssetCounter;
import com.intellij.flex.uiDesigner.css.CssPropertyType;
import com.intellij.flex.uiDesigner.InvalidPropertyException;
import com.intellij.flex.uiDesigner.io.*;
import com.intellij.javascript.flex.FlexMxmlLanguageAttributeNames;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.ColorSampleLookupValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

final class BaseWriter {
  private static final int EMPTY_CLASS_OR_PROPERTY_NAME = 0;
  
  int ARRAY = -1;
  int P_FUD_POSITION = -1;

  private final StringRegistry.StringWriter stringWriter = new StringRegistry.StringWriter();

  private int startPosition;

  private final BlockDataOutputStream blockOut;
  private final PrimitiveAmfOutputStream out;

  private final Scope rootScope = new Scope();
  private int preallocatedId = -1;

  AssetCounter assetCounter;

  public BaseWriter(PrimitiveAmfOutputStream out) {
    this.out = out;
    blockOut = out.getBlockOut();
  }

  public AssetCounter getAssetCounter() {
    return assetCounter;
  }

  @NotNull
  public Scope getRootScope() {
    return rootScope;
  }

  public PrimitiveAmfOutputStream getOut() {
    return out;
  }

  public BlockDataOutputStream getBlockOut() {
    return blockOut;
  }

  public int getPreallocatedId() {
    return preallocatedId;
  }

  // 4
  private int preallocateIdIfNeed() {
    if (!isIdPreallocated()) {
      preallocatedId = rootScope.referenceCounter++;
    }

    return preallocatedId;
  }

  public boolean isIdPreallocated() {
    return preallocatedId != -1;
  }

  public void resetPreallocatedId() {
    preallocatedId = -1;
  }

  public StaticObjectContext createStaticContext(@Nullable Context parentContext, int referencePosition) {
    if (parentContext == null || parentContext.getBackSibling() == null) {
      return new StaticObjectContext(referencePosition, out, preallocatedId, rootScope);
    }
    else {
      return parentContext.getBackSibling().reinitialize(referencePosition, preallocatedId);
    }
  }

  public DynamicObjectContext createDynamicObjectStateContext() {
    return new DynamicObjectContext(preallocatedId, rootScope);
  }

  public void reset() {
    resetAfterMessage();

    ARRAY = -1;
    P_FUD_POSITION = -1;
  }

  private void initNames() {
    ARRAY = getNameReference("array");
    P_FUD_POSITION = getNameReference("$fud_position");
  }

  public void resetAfterMessage() {
    rootScope.referenceCounter = 0;
    stringWriter.finishChange();
  }

  public void addMarker(ByteRange dataRange) {
    blockOut.addMarker(new ByteRangeMarker(blockOut.size(), dataRange));
  }

  public void endObject() {
    out.write(EMPTY_CLASS_OR_PROPERTY_NAME);
  }

  public void beginMessage() {
    stringWriter.startChange();
    if (ARRAY == -1) {
      initNames();
    }

    assert blockOut.getNextMarkerIndex() == 0;
    startPosition = out.size();
  }

  @SuppressWarnings("MethodMayBeStatic")
  public int allocateObjectId(Context context) {
    if (context.getId() == -1) {
      context.setId(context.getParentScope().referenceCounter++);
      context.referenceInitialized();
    }

    return context.getId();
  }

  public int allocateAbsoluteStaticObjectId() {
    return rootScope.referenceCounter++;
  }

  public int getObjectOrFactoryId(@Nullable Context context) {
    return context == null ? preallocateIdIfNeed() : allocateObjectId(context);
  }

  public void endMessage() throws IOException {
    blockOut.beginWritePrepended(stringWriter.size() + IOUtil.uint29SizeOf(rootScope.referenceCounter), startPosition);
    blockOut.writePrepended(stringWriter.getCounter(), stringWriter.getByteArrayOut());
    blockOut.writePrepended(rootScope.referenceCounter);
    blockOut.endWritePrepended(startPosition);
  }

  public int getNameReference(String classOrPropertyName) {
    return stringWriter.getReference(classOrPropertyName);
  }

  public void write(String classOrPropertyName) {
    stringWriter.write(classOrPropertyName, out);
  }

  public void writeVectorHeader(String elementType) {
    out.write(Amf3Types.VECTOR_OBJECT);
    stringWriter.write(elementType, out);
  }

  public void writeProperty(int propertyNameReference, String value) {
    writePropertyHeader(propertyNameReference);
    out.write(Amf3Types.STRING);
    out.writeAmfUtf(value, false);
  }

  public void writeProperty(String propertyName, int value) {
    stringWriter.writeNullable(propertyName, out);
    out.write(PropertyClassifier.PROPERTY);
    out.writeAmfInt(value);
  }

  public void writeIdProperty(String value) {
    write(FlexMxmlLanguageAttributeNames.ID);
    out.write(PropertyClassifier.ID);
    out.writeAmfUtf(value, false);
  }

  public void writeStringReference(String propertyName, String reference) {
    writeStringReference(getNameReference(propertyName), getNameReference(reference));
  }

  public void writeStringReference(int propertyName, String reference) {
    writeStringReference(propertyName, getNameReference(reference));
  }

  public void writeStringReference(int propertyName, int reference) {
    writePropertyHeader(propertyName);
    out.write(AmfExtendedTypes.STRING_REFERENCE);
    out.writeUInt29(reference);
  }

  public void writeStringReference(String reference) {
    out.write(AmfExtendedTypes.STRING_REFERENCE);
    stringWriter.write(reference, out);
  }

  public void writeString(CharSequence value) {
    out.write(Amf3Types.STRING);
    out.writeAmfUtf(value, false);
  }

  public void writeObjectReference(String propertyName, int reference) {
    writeObjectReference(getNameReference(propertyName), reference);
  }

  public void writeObjectReference(int reference) {
    out.write(AmfExtendedTypes.OBJECT_REFERENCE);
    out.writeUInt29(reference);
  }

  public void writeObjectReference(int propertyName, int reference) {
    writePropertyHeader(propertyName);
    writeObjectReference(reference);
  }

  public void writePropertyHeader(int propertyName) {
    out.writeUInt29(propertyName);
    out.write(PropertyClassifier.PROPERTY);
  }

  public void writeObjectReference(int propertyName, Context context) {
    writeObjectReference(propertyName, allocateObjectId(context));
  }

  public void writeObjectHeader(int propertyName, int className) {
    writePropertyHeader(propertyName);
    out.write(Amf3Types.OBJECT);
    writeObjectHeader(className);
  }

  public void writeObjectHeader(String className) {
    writeObjectHeader(getNameReference(className));
  }

  public void writeObjectHeader(String className, int reference) {
    write(className);
    out.writeShort(reference + 1);
  }

  public void writeObjectHeader(int className) {
    out.writeUInt29(className);
    out.allocateClearShort();
  }

  public void writeDocumentFactoryReference(int reference) {
    out.write(AmfExtendedTypes.DOCUMENT_FACTORY_REFERENCE);
    out.writeUInt29(reference);
  }

  public void writeClass(String className) {
    out.write(AmfExtendedTypes.CLASS_REFERENCE);
    write(className);
  }

  public void writeColor(XmlElement element, String value, boolean isPrimitiveStyle) throws InvalidPropertyException {
    out.write(AmfExtendedTypes.COLOR_STYLE_MARKER);
    if (value.charAt(0) == '#') {
      if (isPrimitiveStyle) {
        out.write(CssPropertyType.COLOR_INT);
      }
      value = value.substring(1);
    }
    else if (value.charAt(0) == '0' && value.length() > 2 && value.charAt(1) == 'x') {
      if (isPrimitiveStyle) {
        out.write(CssPropertyType.COLOR_INT);
      }
      value = value.substring(2);
    }
    else {
      final String colorName = value.toLowerCase();
      String hexCodeForColorName = ColorSampleLookupValue.getHexCodeForColorName(colorName);
      if (hexCodeForColorName == null) {
        try {
          long v = Long.parseLong(colorName);
          if (isPrimitiveStyle) {
            out.write(CssPropertyType.COLOR_INT);
          }
          out.writeAmfUInt(v);
          return;
        }
        catch (NumberFormatException e) {
          // Why themeColor for theme halo valid for any other theme? But it is compiler behavior, see
          // example http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/spark/components/Form.html
          // or our test EmbedSwfAndImageFromCss
          if (colorName.equalsIgnoreCase("halogreen")) {
            hexCodeForColorName = "#80FF4D";
          }
          else if (colorName.equalsIgnoreCase("haloblue")) {
            hexCodeForColorName = "#009DFF";
          }
          else if (colorName.equalsIgnoreCase("haloorange")) {
            hexCodeForColorName = "#FFB600";
          }
          else if (colorName.equalsIgnoreCase("halosilver")) {
            hexCodeForColorName = "#AECAD9";
          }
          else {
            throw new InvalidPropertyException(element, "error.invalid.color.name", colorName);
          }
        }
      }
      
      if (isPrimitiveStyle) {
        out.write(CssPropertyType.COLOR_STRING);
        stringWriter.writeNullable(colorName, out);
      }
       
      value = hexCodeForColorName.substring(1);
    }
    
    if (value.length() > 6) {
      out.writeAmfUInt(Long.parseLong(value, 16));
    }
    else {
      out.writeAmfUInt(Integer.parseInt(value, 16));
    }
  }

  public void writeDeferredInstanceFromArray() {
    writeConstructorHeader("com.intellij.flex.uiDesigner.flex.DeferredInstanceFromArray");
    out.write(Amf3Types.ARRAY);
  }

  public void writeConstructorHeader(String className) {
    out.write(Amf3Types.OBJECT);
    writeObjectHeader(className);
    write("1");
  }

  public void writeArrayHeader(int length) {
    out.write(Amf3Types.ARRAY);
    out.writeShort(length);
  }

  public void writeConstructorHeader(String className, int reference) {
    out.write(Amf3Types.OBJECT);
    writeObjectHeader(className, reference);
    write("1");
  }

  public void writeConstructorHeader(int propertyName, String className, int constructorArgType) {
    writePropertyHeader(propertyName);
    writeConstructorHeader(className);
    out.write(constructorArgType);
  }

  public void writeNew(String className, int argumentsLength) {
    out.write(ExpressionMessageTypes.NEW);
    write(className);
    out.write(argumentsLength);
  }
}
