package org.locationtech.geomesa.hbase.filters;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.exceptions.DeserializationException;
import org.apache.hadoop.hbase.filter.Filter.ReturnCode;
import org.apache.hadoop.hbase.filter.FilterBase;
import org.apache.hadoop.hbase.util.Bytes;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.locationtech.geomesa.feature.FeatureEncoding;
import org.locationtech.geomesa.feature.SimpleFeatureDecoder;
import org.locationtech.geomesa.feature.SimpleFeatureDecoder$;
import org.locationtech.geomesa.utils.geotools.SimpleFeatureTypes;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.IOException;

public class JSimpleFeatureFilter extends FilterBase {
    String sftString;
    SimpleFeatureType sft;
    SimpleFeatureDecoder decoder;

    org.opengis.filter.Filter filter;
    String filterString;

    public JSimpleFeatureFilter(String sftString, String filterString) {
        System.out.println("JSFF init sft: " + sftString + " : filter: " + filterString);
        this.sftString = sftString;
        configureSFT();

        this.filterString = filterString;
        configureFilter();
    }

    private void configureSFT() {
        sft = SimpleFeatureTypes.createType("QuickStart", sftString);
        decoder = SimpleFeatureDecoder$.MODULE$.apply(sft, FeatureEncoding.KRYO());
    }

    private void configureFilter() {
        if (filterString != null && filterString != "") {
            try {
                this.filter = ECQL.toFilter(this.filterString);
            } catch (CQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public ReturnCode filterKeyValue(Cell v) throws IOException {

        byte[] encodedSF = CellUtil.cloneValue(v);
        SimpleFeature sf = decoder.decode(encodedSF);

        System.out.println("Who: " + sf.getAttribute("Who") + " When: " + sf.getAttribute("When") +  " Where  " + sf.getAttribute("Where"));

        if (filter == null || filter.evaluate(sf)) {
            // Accept if we have no filter or if the filter passes
            return ReturnCode.INCLUDE;
        } else {
            return ReturnCode.SKIP;
        }
    }

    @Override
    public Cell transformCell(Cell v) throws IOException {
        return super.transformCell(v);
    }

    @Override
    public byte[] toByteArray() throws IOException {
      return Bytes.add(getLengthArray(sftString), getLengthArray(filterString));
    }

    private byte[] getLengthArray(String s) {
        int len = getLen(s);
        if (len == 0) {
            return Bytes.toBytes(0);
        } else {
            return Bytes.add(Bytes.toBytes(len), s.getBytes());
        }
    }

    private int getLen(String s) {
        if (s != null) return s.length();
        else           return 0;
    }

    public static org.apache.hadoop.hbase.filter.Filter parseFrom(final byte [] pbBytes) throws DeserializationException {
        int sftLen =  Bytes.readAsInt(pbBytes, 0, 4);
        String sftString = new String(Bytes.copy(pbBytes, 4, sftLen));

        int filterLen = Bytes.readAsInt(pbBytes, sftLen + 4, 4);
        String filterString = new String(Bytes.copy(pbBytes, sftLen + 8, filterLen));

        return new JSimpleFeatureFilter(sftString, filterString);
    }

}
