/**
 * Dialog — modal shell for every confirmation in the product (start-run
 * confirmation, approve/reject/request-changes, deploy confirmation). Always
 * restate the consequence in plain language before the action buttons —
 * never a bare "Are you sure?".
 *
 * @startingPoint section="Core" subtitle="Confirmation modal shell" viewport="500x320"
 */
export interface DialogProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  /** Plain-language statement of what confirming will do */
  consequence?: string;
  children?: React.ReactNode;
  width?: number;
}
