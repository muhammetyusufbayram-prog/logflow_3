package logflow.model;

/** Projeksiyonla kaldırılmış bir alana erişildiğinde fırlatılır (sessiz yanlış sonuç yerine yüksek sesli hata). */
public class MissingFieldException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public MissingFieldException(String field) {
        super("Alan projeksiyonla kaldırılmış: " + field);
    }
}
