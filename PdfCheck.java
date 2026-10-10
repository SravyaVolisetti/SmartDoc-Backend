import java.io.File;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import java.io.IOException;
import java.util.List;

public class PdfCheck {
    public static void main(String[] args) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(new File(args[0]))) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions)
                        throws IOException {
                    System.out.println("Text: [" + text + "]");
                    for (TextPosition p : positions) {
                        System.out.println(
                            "  Character: " + p.getUnicode()
                            + ", X: " + p.getX()
                            + ", Y: " + p.getY()
                            + ", Width: " + p.getWidth()
                            + ", Height: " + p.getHeight()
                        );
                    }
                }
            };
            stripper.getText(pdf);
        }
    }
}